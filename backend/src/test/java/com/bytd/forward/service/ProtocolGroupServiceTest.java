package com.bytd.forward.service;

import com.bytd.forward.domain.entity.ProtocolGroupEntity;
import com.bytd.forward.domain.repository.ProtocolGroupRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProtocolGroupServiceTest {
    @Test
    void savesTrimmedNameAndDefaultSortAndRejectsDuplicates() {
        ProtocolGroupRepository repository = mock(ProtocolGroupRepository.class);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        ProtocolGroupService service = new ProtocolGroupService(repository);
        var group = service.save(null, "  bridge  ", null);
        assertEquals("bridge", group.getName());
        assertEquals(0, group.getSortOrder());
        when(repository.existsByNameAndIdNot("bridge", -1L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.save(null, "bridge", 0));
        assertThrows(IllegalArgumentException.class, () -> service.save(null, "  ", 0));
    }

    @Test
    void deleteOnlyTouchesGroupRepositoryAndListUsesStableSort() {
        ProtocolGroupRepository repository = mock(ProtocolGroupRepository.class);
        ProtocolGroupEntity group = new ProtocolGroupEntity(); group.setId(2L);
        when(repository.findById(2L)).thenReturn(Optional.of(group));
        ProtocolGroupService service = new ProtocolGroupService(repository);
        service.delete(2L);
        service.list();
        verify(repository).delete(group);
        verify(repository).flush();
        verify(repository).findAllByOrderBySortOrderAscIdAsc();
        // 协议归入未分组由迁移中的 ON DELETE SET NULL 保证，独立 PostgreSQL 环境验收。
    }
}
