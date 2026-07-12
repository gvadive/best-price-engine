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

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

USER_DATA=$(cat <<'EOF'
#!/bin/bash
fallocate -l 2G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
dnf install -y docker
systemctl enable --now docker
mkdir -p /usr/local/lib/docker/cli-plugins
curl -sSL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
curl -sSL https://github.com/docker/buildx/releases/download/v0.19.3/buildx-v0.19.3.linux-amd64 \
  -o /usr/local/lib/docker/cli-plugins/docker-buildx
chmod +x /usr/local/lib/docker/cli-plugins/docker-buildx
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

echo "Copying repo to instance..."
rsync -az --exclude '.git' --exclude '**/build' --exclude '**/node_modules' \
  -e "ssh -o StrictHostKeyChecking=no -i $SSH_KEY" \
  "$REPO_ROOT/" "ec2-user@$PUBLIC_IP:/home/ec2-user/best-price-engine/"

echo "Building and starting the stack..."
ssh -o StrictHostKeyChecking=no -i "$SSH_KEY" "ec2-user@$PUBLIC_IP" \
  "cd best-price-engine && COMPOSE_PARALLEL_LIMIT=1 sudo -E docker compose build ingestion-service pricing-engine-1 pricing-engine-2 && sudo docker compose up -d ingestion-service pricing-engine-1 pricing-engine-2 nginx"

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
