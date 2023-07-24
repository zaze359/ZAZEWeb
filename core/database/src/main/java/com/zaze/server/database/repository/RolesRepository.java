package com.zaze.server.database.repository;


import com.zaze.server.database.model.Roles;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolesRepository extends JpaRepository<Roles, Long> {


//    /**
//     * 根据 roles.items[*].item.name来查找
//     * @param name
//     * @return
//     */
//    List<Roles> findByItems_Item_Name(String name);
}
