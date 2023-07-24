package com.zaze.server.database.model;

import lombok.*;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.Date;

/**
 * 物品
 */
@Entity
@Table(name = "T_ITEM")
@Data
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Item extends BaseEntity{
    @Column
    private String name;

    /**
     * 通过 PersistentMoneyAmount 做映射
     * 指定 currencyCode 为 CNY
     */
    @Column
    @Type(type = "org.jadira.usertype.moneyandcurrency.joda.PersistentMoneyAmount",
            parameters = {@org.hibernate.annotations.Parameter(name = "currencyCode", value = "CNY")})
    private Props price;


    @Builder
    public Item(Long id, Date createTime, Date updateTime, String name, Props price) {
        super(id, createTime, updateTime);
        this.name = name;
        this.price = price;
    }
}
