package com.huobi.client.model.event;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import com.huobi.client.model.Account;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class AccountListEvent {

  private Long timestamp;

  private List<Account> accountList;

}
