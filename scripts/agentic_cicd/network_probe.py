from __future__ import annotations

import argparse
import socket
import time
import urllib.parse


class NetworkAdmissionError(RuntimeError):
    pass


def verify_network_policy(
    *,
    broker_url: str,
    forbidden_endpoints: tuple[tuple[str, int], ...],
    forbidden_dns_endpoint: tuple[str, int],
    settle_seconds: float = 3.0,
) -> None:
    parsed = urllib.parse.urlparse(broker_url)
    if parsed.scheme != "http" or parsed.hostname is None or parsed.port is None:
        raise NetworkAdmissionError("broker URL must contain an internal HTTP host and port")
    if settle_seconds < 1 or settle_seconds > 30:
        raise NetworkAdmissionError("policy settle interval is outside the trusted range")
    time.sleep(settle_seconds)
    if not forbidden_endpoints:
        raise NetworkAdmissionError("at least one forbidden endpoint is required")
    for host, port in forbidden_endpoints:
        try:
            with socket.create_connection((host, port), timeout=2):
                pass
        except OSError:
            continue
        raise NetworkAdmissionError(
            f"forbidden network route is reachable: {host}:{port}"
        )
    _verify_udp_dns_denied(*forbidden_dns_endpoint)
    try:
        with socket.create_connection((parsed.hostname, parsed.port), timeout=5):
            pass
    except OSError as error:
        raise NetworkAdmissionError("artifact broker allow route is not ready") from error


def _verify_udp_dns_denied(host: str, port: int) -> None:
    query = (
        b"\x4a\x53\x01\x00\x00\x01\x00\x00\x00\x00\x00\x00"
        b"\x0akubernetes\x07default\x03svc\x00\x00\x01\x00\x01"
    )
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as client:
            client.settimeout(2)
            client.sendto(query, (host, port))
            response, _ = client.recvfrom(512)
    except OSError:
        return
    if response:
        raise NetworkAdmissionError(
            f"forbidden UDP DNS route is reachable: {host}:{port}"
        )


def main() -> None:
    parser = argparse.ArgumentParser(description="Verify Gate Pod network admission")
    parser.add_argument("--broker-url", required=True)
    parser.add_argument(
        "--forbidden-endpoint",
        action="append",
        default=[],
        help="host:port that must be denied before candidate fetch",
    )
    parser.add_argument(
        "--forbidden-dns-endpoint",
        default="10.96.0.10:53",
        help="UDP DNS host:port that must be denied before candidate fetch",
    )
    arguments = parser.parse_args()
    endpoints: list[tuple[str, int]] = []
    for value in arguments.forbidden_endpoint:
        host, separator, port = value.rpartition(":")
        if not separator or not host or not port.isdigit():
            raise NetworkAdmissionError("forbidden endpoint must be host:port")
        endpoints.append((host, int(port)))
    dns_host, separator, dns_port = arguments.forbidden_dns_endpoint.rpartition(":")
    if not separator or not dns_host or not dns_port.isdigit():
        raise NetworkAdmissionError("forbidden DNS endpoint must be host:port")
    verify_network_policy(
        broker_url=arguments.broker_url,
        forbidden_endpoints=tuple(endpoints),
        forbidden_dns_endpoint=(dns_host, int(dns_port)),
    )


if __name__ == "__main__":
    main()
