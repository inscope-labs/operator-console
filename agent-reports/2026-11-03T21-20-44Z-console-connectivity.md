# console-connectivity-remediation — Stage 1

Timestamp: 2026-11-03T21-20-44Z
Branch: feat/console-connectivity-remediation

## Plan
See docs/console-connectivity-remediation.md (8 stages).

## Stage 1 changes
- GatewayEndpoints.isConfigured — blocks .invalid / empty hosts
- GatewayNotConfiguredException + pre-request check in GatewayHttpClient
- GatewayFailureState.NOT_CONFIGURED, UNREACHABLE + ErrorMapper helpers
- callGateway catches not-configured, UnknownHostException, IOException
- GatewayRepository.health() same mapping
- Unit tests: GatewayEndpointsConfigTest, GatewayCallsTransportTest

## Unverified
- Full Gradle test run in-agent

## Next
Stage 2: remove init-time dashboard/plan network loads
