import os
import socket
import subprocess
import time
import uuid
from pathlib import Path

import pytest
import requests

PROJECT_ROOT = Path(__file__).parent.parent
JAVA_HOME = "/usr/local/Cellar/openjdk@17/17.0.4.1/libexec/openjdk.jdk/Contents/Home"

INGESTION_JAR = PROJECT_ROOT / "ingestion-service/build/libs/ingestion-service-0.1.0.jar"
PRICING_JAR = PROJECT_ROOT / "pricing-engine-service/build/libs/pricing-engine-service-0.1.0.jar"

# Deliberately NOT 8081/8082 -- those are what docker-compose uses, and this
# suite must run in isolation even while the compose stack is up for manual
# testing. A prior version shared those ports; when the port was already
# taken, this fixture's own `java -jar` failed to bind silently, and every
# test ended up hitting the *other*, already-running (and differently
# seeded) instance without any test here noticing.
INGESTION_PORT = 18081
PRICING_PORT = 18082
INGESTION_URL = f"http://localhost:{INGESTION_PORT}"
PRICING_URL = f"http://localhost:{PRICING_PORT}"


def _port_is_free(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        return s.connect_ex(("localhost", port)) != 0


def _start_service(jar_path: Path, port: int) -> subprocess.Popen:
    if not _port_is_free(port):
        raise RuntimeError(
            f"port {port} is already in use -- refusing to start a test instance "
            f"that could silently bind to, or be shadowed by, someone else's process"
        )
    env = os.environ.copy()
    env["JAVA_HOME"] = JAVA_HOME
    env["SERVER_PORT"] = str(port)
    proc = subprocess.Popen(
        ["java", "-jar", str(jar_path)],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        env=env,
    )
    return proc


def _wait_until_up(url: str, proc: subprocess.Popen, timeout: int = 30):
    deadline = time.time() + timeout
    last_error = None
    while time.time() < deadline:
        if proc.poll() is not None:
            raise RuntimeError(f"process exited early (code {proc.returncode}) before {url} came up")
        try:
            requests.get(url, timeout=1)
            return
        except requests.exceptions.ConnectionError as e:
            last_error = e
            time.sleep(0.5)
    raise TimeoutError(f"{url} did not come up within {timeout}s: {last_error}")


@pytest.fixture(scope="session")
def ingestion_process():
    proc = _start_service(INGESTION_JAR, INGESTION_PORT)
    _wait_until_up(f"{INGESTION_URL}/api/offers?product=warmup", proc)
    yield proc
    if proc.poll() is None:
        proc.kill()
        proc.wait()


@pytest.fixture
def auth_session(ingestion_process):
    """A requests.Session authenticated as a freshly-registered, uniquely-named user --
    placing orders / price watch orders now requires auth, so tests that exercise those
    endpoints need a logged-in session rather than the bare `requests` module."""
    session = requests.Session()
    username = f"testuser-{uuid.uuid4().hex[:8]}"
    password = "testpass123"
    session.post(f"{INGESTION_URL}/api/auth/register", json={"username": username, "password": password})
    session.post(f"{INGESTION_URL}/api/auth/login", json={"username": username, "password": password})
    session.username = username
    return session


@pytest.fixture(scope="session")
def pricing_process(ingestion_process):
    env_override = {"INGESTION_BASE_URL": INGESTION_URL}
    os.environ.update(env_override)
    proc = _start_service(PRICING_JAR, PRICING_PORT)
    _wait_until_up(f"{PRICING_URL}/api/compare?product=warmup", proc)
    yield proc
    if proc.poll() is None:
        proc.kill()
        proc.wait()


@pytest.fixture(scope="session")
def seeded_usb_cable(ingestion_process):
    requests.post(f"{INGESTION_URL}/api/offers", json={
        "productName": "USB Cable",
        "retailer": "RetailerA",
        "basePrice": 8.00,
        "deliveryDays": 2,
        "rating": 4.5,
        "inStock": True,
        "availableQuantity": 999,
        "quantityTiers": [],
    })
    requests.post(f"{INGESTION_URL}/api/offers", json={
        "productName": "USB Cable",
        "retailer": "RetailerB",
        "basePrice": 9.50,
        "deliveryDays": 1,
        "rating": 4.9,
        "inStock": True,
        "availableQuantity": 999,
        "quantityTiers": [{"minQuantity": 5, "unitPrice": 6.50}],
    })
    return "USB Cable"
