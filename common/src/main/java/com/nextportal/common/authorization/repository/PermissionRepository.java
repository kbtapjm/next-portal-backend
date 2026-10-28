package com.nextportal.common.authorization.repository;

import com.nextportal.common.authorization.entity.Permission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, String> {
    List<Permission> findAllByOrderByCodeAsc();
}
