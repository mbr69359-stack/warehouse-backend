package com.warehouse;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.warehouse.entity.SysUser;
import com.warehouse.mapper.SysUserMapper;
import java.util.TimeZone;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.warehouse.mapper")
public class WarehouseApplication {
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Nairobi"));
        SpringApplication.run(WarehouseApplication.class, args);
    }

    @Bean
    public CommandLineRunner initAdminPassword(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder) {
        return args -> {
            SysUser admin = sysUserMapper.selectOne(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "admin"));
            if (admin != null) {
                admin.setPassword(passwordEncoder.encode("admin123"));
                sysUserMapper.updateById(admin);
            }
        };
    }
}
