package com.zaze.server.database.model;

import lombok.*;
import org.hibernate.annotations.Type;
import org.joda.money.Money;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.OneToOne;
import javax.persistence.Table;

/**
 * 道具
 */
@Entity
@Table(name = "T_PROPS")
@Builder
@EqualsAndHashCode(callSuper = true)
@Data
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class Props extends BaseEntity {
    /**
     * 通过 PersistentMoneyAmount 做映射
     * 指定 currencyCode 为 CNY
     */
    @Column
    @Type(type = "org.jadira.usertype.moneyandcurrency.joda.PersistentMoneyAmount",
            parameters = {@org.hibernate.annotations.Parameter(name = "currencyCode", value = "CNY")})
    private Money price;

    @OneToOne
    private Item item;
}
