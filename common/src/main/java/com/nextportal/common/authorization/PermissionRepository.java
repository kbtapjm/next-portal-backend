package com.nextportal.common.authorization;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, String> {
    List<Permission> findAllByOrderByCodeAsc();
}
