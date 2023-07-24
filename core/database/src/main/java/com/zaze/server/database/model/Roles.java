package com.zaze.server.database.model;


import lombok.Builder;
import lombok.Data;

import javax.persistence.*;
import java.util.List;

@Entity
@Table(name = "T_ROLES")
@Builder
@Data
public class Roles {
    /** 主键由数据库自动生成 **/
    @Id
    @GeneratedValue
    private Long id;
    @Column
    private String name;

    @Column
    private int rarity;
    /**
     * 通过 T_ROLES_PROPS 这张表关联
     * 多对多映射
     */
    @ManyToMany
    @JoinTable(name = "T_ROLES_PROPS")
    private List<Props> items;
}

