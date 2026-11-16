# Party menu component check

Run from the repository root with Java 25:

```sh
sh ./gradlew -Pmc=26.2 -I mock-checks/party-menu/party-menu.init.gradle checkPartyMenuMock --no-configuration-cache
```

This optional fixture uses the same pinned MockBukkit 26.2 version as the One Shot fixture. It adds no production dependency. It runs the actual QueueMenu and QueueService with Paper inventories and checks joining a mode and tournament, Back preserving membership, leaving, stale leave actions, disabled-mode slot stability and open-menu transitions when a Party session starts or stops. It also checks Medium as the default length, a Marathon click reaching the queue decision, spectating with all queues disabled and closing the browsing inventory for an active participant.

MockBukkit initializes Paper registries but does not validate the native client's visual layout, hover positioning or network behavior. The regular build compiles the menu and validates both language catalogs on the selected Paper API.
