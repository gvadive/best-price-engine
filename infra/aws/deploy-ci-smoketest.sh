#!/bin/bash
# Launches an ephemeral EC2 instance, deploys the docker-compose stack onto it,
# smoke-tests the real endpoints through nginx, then always terminates the
# instance again. Used by CI on every push to develop; also runnable by hand
# for a dry run (pass --keep to skip the teardown so you can inspect it).
set -euo pipefail

REGION="eu-west-2"
AMI_ID="ami-002aab1cab5a08e35"          # Amazon Linux 2023, eu-west-2, x86_64
SUBNET_ID="subnet-0ae9552afefbdee11"
SG_ID="sg-0dea0be7eb445d2c4"
KEY_NAME="best-price-engine-ci"
SSH_KEY="${SSH_KEY:-/tmp/best-price-engine-ci.pem}"
KEEP=0
[ "${1:-}" = "--keep" ] && KEEP=1

# Images are built once in CI and pulled here -- no on-box Gradle compilation,
# so no swap file / buildx / COMPOSE_PARALLEL_LIMIT workaround is needed
# anymore (those existed solely to survive two Gradle daemons fighting over
# 1GiB of RAM). Defaults let a manual dry run (`bash deploy-ci-smoketest.sh`)
# still work against the images already pushed to ghcr.io.
GHCR_OWNER="${GHCR_OWNER:-gvadive}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
GHCR_USER="${GHCR_USER:-$GHCR_OWNER}"
GHCR_TOKEN="${GHCR_TOKEN:-}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

USER_DATA=$(cat <<'EOF'
#!/bin/bash
dnf install -y docker
systemctl enable --now docker
mkdir -p /usr/local/lib/docker/cli-plugins
curl -sSL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
usermod -aG docker ec2-user
EOF
)

echo "Launching instance..."
INSTANCE_ID=$(aws ec2 run-instances \
  --region "$REGION" \
  --image-id "$AMI_ID" \
  --instance-type t3.micro \
  --key-name "$KEY_NAME" \
  --subnet-id "$SUBNET_ID" \
  --security-group-ids "$SG_ID" \
  --associate-public-ip-address \
  --tag-specifications "ResourceType=instance,Tags=[{Key=Project,Value=best-price-engine},{Key=Name,Value=best-price-engine-ci-smoketest}]" \
  --user-data "$USER_DATA" \
  --query 'Instances[0].InstanceId' --output text)
echo "Instance: $INSTANCE_ID"

cleanup() {
  if [ "$KEEP" = "1" ]; then
    echo "Skipping teardown (--keep). Terminate manually with:"
    echo "  aws ec2 terminate-instances --region $REGION --instance-ids $INSTANCE_ID"
    return
  fi
  echo "Terminating $INSTANCE_ID..."
  aws ec2 terminate-instances --region "$REGION" --instance-ids "$INSTANCE_ID" --output text >/dev/null || true
}
trap cleanup EXIT

echo "Waiting for instance to be running..."
aws ec2 wait instance-running --region "$REGION" --instance-ids "$INSTANCE_ID"

PUBLIC_IP=$(aws ec2 describe-instances --region "$REGION" --instance-ids "$INSTANCE_ID" \
  --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)
echo "Public IP: $PUBLIC_IP"

echo "Waiting for SSH..."
for i in $(seq 1 30); do
  if ssh -o StrictHostKeyChecking=no -o ConnectTimeout=5 -i "$SSH_KEY" "ec2-user@$PUBLIC_IP" "echo ok" 2>/dev/null; then
    break
  fi
  sleep 10
done

echo "Waiting for docker to be ready on the instance..."
for i in $(seq 1 30); do
  if ssh -o StrictHostKeyChecking=no -i "$SSH_KEY" "ec2-user@$PUBLIC_IP" "sudo docker info >/dev/null 2>&1"; then
    break
  fi
  sleep 10
done

echo "Copying compose files to instance..."
# No Java source, no Gradle wrapper, no nginx.conf, no frontend source -- all
# three images (ingestion, pricing, and now the React frontend baked into
# nginx) are pulled from ghcr.io fully built, so only the two compose files
# that describe which images/ports to use need to travel.
ssh -o StrictHostKeyChecking=no -i "$SSH_KEY" "ec2-user@$PUBLIC_IP" "mkdir -p best-price-engine"
rsync -az \
  -e "ssh -o StrictHostKeyChecking=no -i $SSH_KEY" \
  "$REPO_ROOT/docker-compose.yml" "$REPO_ROOT/docker-compose.ci.yml" \
  "ec2-user@$PUBLIC_IP:/home/ec2-user/best-price-engine/"

echo "Pulling images from ghcr.io and starting the stack..."
if [ -n "$GHCR_TOKEN" ]; then
  ssh -o StrictHostKeyChecking=no -i "$SSH_KEY" "ec2-user@$PUBLIC_IP" \
    "echo '$GHCR_TOKEN' | sudo docker login ghcr.io -u '$GHCR_USER' --password-stdin"
fi
ssh -o StrictHostKeyChecking=no -i "$SSH_KEY" "ec2-user@$PUBLIC_IP" \
  "cd best-price-engine && sudo GHCR_OWNER='$GHCR_OWNER' IMAGE_TAG='$IMAGE_TAG' docker compose -f docker-compose.yml -f docker-compose.ci.yml pull ingestion-service pricing-engine-1 pricing-engine-2 nginx && sudo GHCR_OWNER='$GHCR_OWNER' IMAGE_TAG='$IMAGE_TAG' docker compose -f docker-compose.yml -f docker-compose.ci.yml up -d ingestion-service pricing-engine-1 pricing-engine-2 nginx"

echo "Waiting for the stack to become reachable..."
# NOTE: must poll through nginx (8080) — the security group only opens 22/8080
# inbound, so a direct curl to ingestion-service's own port (8081) from off-box
# hangs on a filtered connect() instead of failing fast.
for i in $(seq 1 60); do
  code=$(curl -s --max-time 5 -o /dev/null -w "%{http_code}" "http://$PUBLIC_IP:8080/api/offers?product=warmup" || true)
  [ "$code" = "200" ] && echo "stack ready after ${i}0s" && break
  sleep 10
done

echo "Smoke-testing through nginx ($PUBLIC_IP:8080)..."
FRONTEND_CODE=$(curl -s --max-time 5 -o /dev/null -w "%{http_code}" "http://$PUBLIC_IP:8080/")
OFFERS_CODE=$(curl -s --max-time 5 -o /dev/null -w "%{http_code}" "http://$PUBLIC_IP:8080/api/offers?product=warmup")

echo "Frontend root: $FRONTEND_CODE   /api/offers via nginx: $OFFERS_CODE"

if [ "$FRONTEND_CODE" != "200" ] || [ "$OFFERS_CODE" != "200" ]; then
  echo "SMOKE TEST FAILED"
  exit 1
fi

echo "SMOKE TEST PASSED — live at http://$PUBLIC_IP:8080 (will be torn down on exit unless --keep)"
