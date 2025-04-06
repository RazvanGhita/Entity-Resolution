package com.example.EntityResolution_3.service;

import com.example.EntityResolution_3.model.UserPrincipal;
import com.example.EntityResolution_3.model.Users;
import com.example.EntityResolution_3.repository.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


@Service
public class MyUserDetailsService implements UserDetailsService {

    @Autowired
    private final UserRepo userRepo;

    public MyUserDetailsService(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    /*@Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Users user = userRepo.findByUsername(username);
        if (user == null) {
            System.out.println("User Not Found");
            throw new UsernameNotFoundException("user not found");
        }

        return new UserPrincipal(user);
    }*/
   /* @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return UserRepo.findByUsername(username)
                .map(user -> new User(
                        user.getUsername(),
                        user.getPassword(),
                        Collections.emptyList() // Provide roles/authorities if necessary
                ))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }*/
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Users user = userRepo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Return a custom UserPrincipal instance
        return new UserPrincipal(user);  // This will contain authorities as well
    }
}
