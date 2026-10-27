package com.nextportal.common.authorization;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface RoleRepository extends JpaRepository<PortalRole, String> {
    List<PortalRole> findAllByOrderByCodeAsc();

    @Query("select distinct r from PortalRole r left join fetch r.permissions where r in (select role from Member m join m.roles role where m.id = :memberId)")
    List<PortalRole> findForMember(long memberId);

    // All role/membership writes take the same lock to protect assignment/deletion and the last administrator.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PortalRole r where r.code = 'ADMIN'")
    Optional<PortalRole> lockAdministration();
}
