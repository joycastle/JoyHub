package com.iflytek.skillhub.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.organization.FeishuDepartmentMembershipSyncService;
import com.iflytek.skillhub.auth.organization.FeishuDirectoryClient;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class FeishuDepartmentSyncTaskTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private FeishuDirectoryClient directoryClient;

    @Mock
    private FeishuDepartmentMembershipSyncService membershipSyncService;

    @Test
    void synchronizeAll_backfillsExistingActiveFeishuUsers() {
        UserAccount user = new UserAccount("feishu:ou-user", "用户", null, null);
        user.setStatus(UserStatus.ACTIVE);
        var departments = List.of(new FeishuDirectoryClient.FeishuDepartment("od-1", "研发部"));
        when(userAccountRepository.findByIdStartingWithAndStatus(
                org.mockito.ArgumentMatchers.eq("feishu:"),
                org.mockito.ArgumentMatchers.eq(UserStatus.ACTIVE),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(user)));
        when(directoryClient.loadDepartments("ou-user")).thenReturn(Optional.of(departments));

        new FeishuDepartmentSyncTask(
                userAccountRepository,
                directoryClient,
                membershipSyncService,
                true
        ).synchronizeAll();

        verify(membershipSyncService).synchronize("feishu:ou-user", departments);
    }
}
