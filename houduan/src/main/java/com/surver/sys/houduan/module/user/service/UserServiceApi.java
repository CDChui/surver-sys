package com.surver.sys.houduan.module.user.service;

import com.surver.sys.houduan.module.user.dto.CreateUserRequest;
import com.surver.sys.houduan.module.user.dto.UpdateUserRequest;
import com.surver.sys.houduan.module.user.dto.UserItemResponse;
import com.surver.sys.houduan.module.user.model.UserModel;

import java.util.List;
import java.util.Optional;

public interface UserServiceApi {

    List<UserItemResponse> listUsers();

    UserItemResponse createUser(CreateUserRequest request);

    UserItemResponse updateUser(Long id, UpdateUserRequest request);

    void deleteUser(Long id);

    void updateRole(Long id, String role);

    void updateStatus(Long id, String status);

    void resetLocalUserPassword(Long id, String newPassword);

    void changeOwnLocalPassword(Long userId, String oldPassword, String newPassword);

    Optional<UserModel> findByUsername(String username);

    UserModel getById(Long id);

    boolean verifyLocalPassword(String username, String password);

    Optional<UserModel> findLocalUserByUsername(String username);

    UserModel findOrCreateOauthUser(String username, String realName, String role);
}
