package com.example.musing_BE.user.repository;

import com.example.musing_BE.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
