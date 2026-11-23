package com.smartdoor.backend.onboarding;

import com.smartdoor.backend.identity.entity.Account;
import com.smartdoor.backend.identity.repository.AccountRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Deployment operator supplies the first technician credentials through private environment. */
@Component
@org.springframework.context.annotation.Profile("!provision & !device-security")
public class TechnicianBootstrap implements ApplicationRunner {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final Environment environment;
    public TechnicianBootstrap(AccountRepository accounts,PasswordEncoder passwords,Environment environment) {
        this.accounts=accounts; this.passwords=passwords; this.environment=environment;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        String username=environment.getProperty("app.onboarding.technician-username","").strip();
        String password=environment.getProperty("app.onboarding.technician-password","");
        if (username.isEmpty() && password.isEmpty()) return;
        if (!username.matches("[a-z0-9._-]{3,50}") || password.length()<12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new IllegalStateException("Configure a technician username and private password of 12–72 bytes.");
        var previous=accounts.findByUsername(username);
        if (previous.isPresent()) {
            if (previous.get().getRole()!=Account.Role.TECHNICIAN) throw new IllegalStateException("Technician bootstrap username is already used by a customer.");
            return; // Preserve passwords changed through the App.
        }
        var account=new Account(); account.setUsername(username); account.setDisplayName("Kỹ thuật viên");
        account.setPasswordHash(passwords.encode(password)); account.setRole(Account.Role.TECHNICIAN);
        accounts.saveAndFlush(account);
    }
}
