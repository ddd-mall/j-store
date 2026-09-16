from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / 'j-store-outbox-core/src/main/kotlin/com/jstore/outbox'
SPRING = ROOT / 'j-store-outbox-spring/src/main/kotlin/com/jstore/outbox/spring'


class OutboxBackendBoundaryTest(unittest.TestCase):
    def test_core_does_not_own_polling_state(self):
        for path in CORE.glob('*.kt'):
            source = path.read_text(encoding='utf-8')
            for implementation_type in ('OutboxEntryStatus', 'OutboxEntryRepository', 'lockToken', 'lockedUntil'):
                self.assertNotIn(implementation_type, source, str(path))

    def test_publishers_only_append_immutable_messages(self):
        for relative in ('OutboxEventPublisher.kt', 'messaging/OutboxIntegrationMessagePublisher.kt'):
            source = (SPRING / relative).read_text(encoding='utf-8')
            self.assertIn('OutboxWriter', source)
            self.assertIn('OutboxMessage(', source)
            self.assertNotIn('OutboxEntryRepository', source)
            self.assertNotIn('OutboxRelaySignal', source)
            self.assertNotIn('OutboxEntryStatus', source)


if __name__ == '__main__':
    unittest.main()
