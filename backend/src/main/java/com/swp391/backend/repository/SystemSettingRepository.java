package com.swp391.backend.repository;

import com.swp391.backend.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long> {
    Optional<SystemSetting> findBySettingKey(String settingKey);
    List<SystemSetting> findBySettingGroupOrderBySettingKeyAsc(String settingGroup);
}
