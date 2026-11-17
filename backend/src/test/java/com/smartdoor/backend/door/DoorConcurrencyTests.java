package com.smartdoor.backend.door;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdoor.backend.door.service.DoorProvisioningService;
import com.smartdoor.backend.door.service.DoorService;
import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.repository.AccountRepository;
import com.smartdoor.backend.shared.error.BadRequestException;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** H2 kiểm tra việc tuần tự hóa; cần chạy thêm trên MySQL để xác minh ràng buộc thực tế. */
@SpringBootTest
@ActiveProfiles("test")
class DoorConcurrencyTests {
	@Autowired DoorProvisioningService provisioning;
	@Autowired DoorService service;
	@Autowired AccountRepository accounts;

	@Test
	void onlyOneAccountWinsAndWinnerCanRetry() throws Exception {
		String suffix = UUID.randomUUID().toString();
		var provisioned = provisioning.provision("Concurrent claim", null, "C1-" + suffix, "C2-" + suffix);
		Account first = account();
		Account second = account();
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var a = executor.submit(claim(start, first.getId(), provisioned.activationCode()));
			var b = executor.submit(claim(start, second.getId(), provisioned.activationCode()));
			start.countDown();
			Long winnerA = a.get(15, TimeUnit.SECONDS);
			Long winnerB = b.get(15, TimeUnit.SECONDS);
			assertThat((winnerA == null ? 0 : 1) + (winnerB == null ? 0 : 1)).isEqualTo(1);
			Long winner = winnerA == null ? winnerB : winnerA;
			assertThat(service.claimDoor(winner, provisioned.activationCode()).id()).isEqualTo(provisioned.doorId());
		}
	}

	private Callable<Long> claim(CountDownLatch start, Long accountId, String code) {
		return () -> {
			if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Claim barrier timed out");
			try {
				service.claimDoor(accountId, code);
				return accountId;
			} catch (BadRequestException expectedForLoser) {
				return null;
			}
		};
	}

	@Test
	void simultaneousRetriesFromSameAccountBothReturnTheExistingDoor() throws Exception {
		String suffix = UUID.randomUUID().toString();
		var provisioned = provisioning.provision("Concurrent retry", null, "T1-" + suffix, "T2-" + suffix);
		Long accountId = account().getId();
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var a = executor.submit(claim(start, accountId, provisioned.activationCode()));
			var b = executor.submit(claim(start, accountId, provisioned.activationCode()));
			start.countDown();
			assertThat(a.get(15, TimeUnit.SECONDS)).isEqualTo(accountId);
			assertThat(b.get(15, TimeUnit.SECONDS)).isEqualTo(accountId);
			assertThat(service.listDoors(accountId, 0, 20).totalElements()).isEqualTo(1);
		}
	}
	private Account account() {
		Account account = new Account();
		account.setDisplayName("Concurrency test");
		account.setUsername(UUID.randomUUID().toString());
		account.setPasswordHash("test-placeholder");
		return accounts.saveAndFlush(account);
	}
}
