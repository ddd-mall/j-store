from __future__ import annotations

import unittest
from unittest.mock import patch

from scripts.agentic_cicd.network_probe import (
    NetworkAdmissionError,
    verify_network_policy,
)


class _Connection:
    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False


class _UdpSocket:
    def __init__(self, response: bytes | None = None):
        self.response = response

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def settimeout(self, _timeout):
        pass

    def sendto(self, _payload, _endpoint):
        pass

    def recvfrom(self, _size):
        if self.response is None:
            raise TimeoutError()
        return self.response, ("10.96.0.10", 53)


class NetworkProbeTest(unittest.TestCase):
    @patch("scripts.agentic_cicd.network_probe.time.sleep", return_value=None)
    @patch("scripts.agentic_cicd.network_probe.socket.create_connection")
    @patch("scripts.agentic_cicd.network_probe.socket.socket")
    def test_requires_api_denial_before_broker_allow(
        self, udp_socket, connect, _sleep
    ) -> None:
        udp_socket.return_value = _UdpSocket()
        connect.side_effect = [TimeoutError(), TimeoutError(), _Connection()]
        verify_network_policy(
            broker_url="http://10.96.200.81:8081",
            forbidden_endpoints=(("10.96.0.1", 443), ("10.96.0.10", 53)),
            forbidden_dns_endpoint=("10.96.0.10", 53),
        )
        self.assertEqual(3, connect.call_count)

    @patch("scripts.agentic_cicd.network_probe.time.sleep", return_value=None)
    @patch("scripts.agentic_cicd.network_probe.socket.create_connection")
    def test_rejects_when_api_is_reachable(self, connect, _sleep) -> None:
        connect.return_value = _Connection()
        with self.assertRaisesRegex(NetworkAdmissionError, "forbidden"):
            verify_network_policy(
                broker_url="http://10.96.200.81:8081",
                forbidden_endpoints=(("10.96.0.1", 443),),
                forbidden_dns_endpoint=("10.96.0.10", 53),
            )

    @patch("scripts.agentic_cicd.network_probe.time.sleep", return_value=None)
    @patch("scripts.agentic_cicd.network_probe.socket.create_connection")
    @patch("scripts.agentic_cicd.network_probe.socket.socket")
    def test_rejects_when_udp_dns_is_reachable(
        self, udp_socket, connect, _sleep
    ) -> None:
        connect.side_effect = [TimeoutError()]
        udp_socket.return_value = _UdpSocket(b"dns-response")
        with self.assertRaisesRegex(NetworkAdmissionError, "UDP DNS"):
            verify_network_policy(
                broker_url="http://10.96.200.81:8081",
                forbidden_endpoints=(("10.96.0.1", 443),),
                forbidden_dns_endpoint=("10.96.0.10", 53),
            )

    @patch("scripts.agentic_cicd.network_probe.time.sleep", return_value=None)
    def test_requires_at_least_one_negative_route(self, _sleep) -> None:
        with self.assertRaisesRegex(NetworkAdmissionError, "at least one"):
            verify_network_policy(
                broker_url="http://10.96.200.81:8081",
                forbidden_endpoints=(),
                forbidden_dns_endpoint=("10.96.0.10", 53),
            )


if __name__ == "__main__":
    unittest.main()
