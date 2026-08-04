package com.iflytek.skillhub.task;

import com.iflytek.skillhub.auth.organization.FeishuDepartmentMembershipSyncService;
import com.iflytek.skillhub.auth.organization.FeishuDirectoryClient;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically backfills Feishu department membership for active users, including existing users. */
@Component
public class FeishuDepartmentSyncTask {

    private static final Logger log = LoggerFactory.getLogger(FeishuDepartmentSyncTask.class);
    private static final String USER_ID_PREFIX = "feishu:";
    private static final int PAGE_SIZE = 100;

    private final UserAccountRepository userAccountRepository;
    private final FeishuDirectoryClient directoryClient;
    private final FeishuDepartmentMembershipSyncService membershipSyncService;
    private final boolean enabled;

    public FeishuDepartmentSyncTask(
            UserAccountRepository userAccountRepository,
            FeishuDirectoryClient directoryClient,
            FeishuDepartmentMembershipSyncService membershipSyncService,
            @Value("${joyhub.feishu.department-sync.enabled:true}") boolean enabled) {
        this.userAccountRepository = userAccountRepository;
        this.directoryClient = directoryClient;
        this.membershipSyncService = membershipSyncService;
        this.enabled = enabled;
    }

    @Scheduled(
            initialDelayString = "${joyhub.feishu.department-sync.initial-delay-ms:30000}",
            fixedDelayString = "${joyhub.feishu.department-sync.fixed-delay-ms:21600000}"
    )
    public void synchronizeAll() {
        if (!enabled) {
            return;
        }
        int pageNumber = 0;
        int synchronizedUsers = 0;
        Page<UserAccount> page;
        do {
            page = userAccountRepository.findByIdStartingWithAndStatus(
                    USER_ID_PREFIX,
                    UserStatus.ACTIVE,
                    PageRequest.of(pageNumber, PAGE_SIZE)
            );
            for (UserAccount user : page.getContent()) {
                String openId = user.getId().substring(USER_ID_PREFIX.length());
                try {
                    var departments = directoryClient.loadDepartments(openId);
                    if (departments.isPresent()) {
                        membershipSyncService.synchronize(user.getId(), departments.get());
                        synchronizedUsers++;
                    }
                } catch (RuntimeException ex) {
                    log.warn("Scheduled Feishu department sync failed for userId={}: {}", user.getId(), ex.getMessage());
                }
            }
            pageNumber++;
        } while (page.hasNext());
        log.info("Feishu department synchronization completed [users={}]", synchronizedUsers);
    }
}
