package com.specialweek.utils;

import com.specialweek.dto.UserDTO;

/**
 * @author specialweek
 * @since 2026-08-15
 */
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
