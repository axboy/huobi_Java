package com.huobi.client.impl;

import static com.huobi.client.impl.utils.InternalUtils.await;

import com.huobi.client.SubscriptionErrorHandler;
import com.huobi.client.SubscriptionListener;
import com.huobi.client.impl.utils.Channels;
import com.huobi.client.impl.utils.JsonWrapper;
import com.huobi.client.impl.utils.JsonWrapperArray;
import com.huobi.client.impl.utils.TimeService;
import com.huobi.client.model.Account;
import com.huobi.client.model.AccountChange;
import com.huobi.client.model.Balance;
import com.huobi.client.model.Candlestick;
import com.huobi.client.model.DepthEntry;
import com.huobi.client.model.Order;
import com.huobi.client.model.OrderUpdate;
import com.huobi.client.model.PriceDepth;
import com.huobi.client.model.Trade;
import com.huobi.client.model.TradeStatistics;
import com.huobi.client.model.enums.AccountChangeType;
import com.huobi.client.model.enums.AccountState;
import com.huobi.client.model.enums.AccountType;
import com.huobi.client.model.enums.BalanceMode;
import com.huobi.client.model.enums.BalanceType;
import com.huobi.client.model.enums.CandlestickInterval;
import com.huobi.client.model.enums.DealRole;
import com.huobi.client.model.enums.DepthStep;
import com.huobi.client.model.enums.OrderSource;
import com.huobi.client.model.enums.OrderState;
import com.huobi.client.model.enums.OrderType;
import com.huobi.client.model.enums.TradeDirection;
import com.huobi.client.model.event.AccountEvent;
import com.huobi.client.model.event.AccountListEvent;
import com.huobi.client.model.event.CandlestickEvent;
import com.huobi.client.model.event.CandlestickReqEvent;
import com.huobi.client.model.event.OrderUpdateEvent;
import com.huobi.client.model.event.OrderUpdateNewEvent;
import com.huobi.client.model.event.PriceDepthEvent;
import com.huobi.client.model.event.TradeEvent;
import com.huobi.client.model.event.TradeStatisticsEvent;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

class WebsocketRequestImpl {

  private final String apiKey;

  WebsocketRequestImpl(String apiKey) {
    this.apiKey = apiKey;
  }

  WebsocketRequest<CandlestickEvent> subscribeCandlestickEvent(
      List<String> symbols,
      CandlestickInterval interval,
      SubscriptionListener<CandlestickEvent> subscriptionListener,
      SubscriptionErrorHandler errorHandler) {
    InputChecker.checker()
        .checkSymbolList(symbols)
        .shouldNotNull(subscriptionListener, "listener")
        .shouldNotNull(interval, "CandlestickInterval");
    WebsocketRequest<CandlestickEvent> request =
        new WebsocketRequest<>(subscriptionListener, errorHandler);
    if (symbols.size() == 1) {
      request.name = "Candlestick for " + symbols;
    } else {
      request.name = "Candlestick for " + symbols + " ...";
    }
    request.connectionHandler = (connection) ->
        symbols.stream()
            .map((symbol) -> Channels.klineChannel(symbol, interval))
            .forEach(req -> {
              connection.send(req);
              await(1);
            });
    request.jsonParser = (jsonWrapper) -> {
      String ch = jsonWrapper.getString("ch");
      ChannelParser parser = new ChannelParser(ch);
      CandlestickEvent candlestickEvent = new CandlestickEvent();
      candlestickEvent.setSymbol(parser.getSymbol());
      candlestickEvent.setInterval(interval);
      candlestickEvent.setTimestamp(
          TimeService.convertCSTInMillisecondToUTC(jsonWrapper.getLong("ts")));
      JsonWrapper tick = jsonWrapper.getJsonObject("tick");
      Candlestick data = new Candlestick();
      data.setTimestamp(TimeService.convertCSTInSecondToUTC(tick.getLong("id")));
      data.setOpen(tick.getBigDecimal("open"));
      data.setClose(tick.getBigDecimal("close"));
      data.setLow(tick.getBigDecimal("low"));
      data.setHigh(tick.getBigDecimal("high"));
      data.setAmount(tick.getBigDecimal("amount"));
      data.setCount(tick.getLong("count"));
      data.setVolume(tick.getBigDecimal("vol"));
      candlestickEvent.setData(data);
      return candlestickEvent;
    };
    return request;
  }


  WebsocketRequest<CandlestickReqEvent> requestCandlestickEvent(
      List<String> symbols, Long from, Long to,
      CandlestickInterval interval,
      SubscriptionListener<CandlestickReqEvent> subscriptionListener,
      SubscriptionErrorHandler errorHandler) {

    InputChecker.checker()
        .checkSymbolList(symbols)
        .shouldNotNull(subscriptionListener, "listener")
        .shouldNotNull(interval, "CandlestickInterval");
    WebsocketRequest<CandlestickReqEvent> request =
        new WebsocketRequest<>(subscriptionListener, errorHandler);
    if (symbols.size() == 1) {
      request.name = "Candlestick for " + symbols;
    } else {
      request.name = "Candlestick for " + symbols + " ...";
    }
    request.connectionHandler = (connection) ->
        symbols.stream()
            .map((symbol) -> Channels.klineReqChannel(symbol, interval, from, to))
            .forEach(req -> {
              connection.send(req);
              await(1);
            });
    request.jsonParser = (jsonWrapper) -> {
      String ch = jsonWrapper.getString("rep");
      ChannelParser parser = new ChannelParser(ch);
      CandlestickReqEvent candlestickEvent = new CandlestickReqEvent();
      candlestickEvent.setSymbol(parser.getSymbol());
      candlestickEvent.setInterval(interval);
      JsonWrapperArray dataArray = jsonWrapper.getJsonArray("data");

      List<Candlestick> list = new ArrayList<>();
      dataArray.forEach(dataJson -> {
        Candlestick data = new Candlestick();
        data.setTimestamp(TimeService.convertCSTInSecondToUTC(dataJson.getLong("id")));
        data.setOpen(dataJson.getBigDecimal("open"));
        data.setClose(dataJson.getBigDecimal("close"));
        data.setLow(dataJson.getBigDecimal("low"));
        data.setHigh(dataJson.getBigDecimal("high"));
        data.setAmount(dataJson.getBigDecimal("amount"));
        data.setCount(dataJson.getLong("count"));
        data.setVolume(dataJson.getBigDecimal("vol"));
        list.add(data);
      });

      candlestickEvent.setData(list);
      return candlestickEvent;
    };
    return request;


  }

  WebsocketRequest<TradeEvent> subscribeTradeEvent(
      List<String> symbols,
      SubscriptionListener<TradeEvent> subscriptionListener,
      SubscriptionErrorHandler errorHandler) {
    InputChecker.checker().checkSymbolList(symbols).shouldNotNull(subscriptionListener, "listener");
    WebsocketRequest<TradeEvent> request =
        new WebsocketRequest<>(subscriptionListener, errorHandler);
    if (symbols.size() == 1) {
      request.name = "Trade for " + symbols;
    } else {
      request.name = "Trade for " + symbols + " ...";
    }
    request.connectionHandler = (connection) ->
        symbols.stream()
            .map(Channels::tradeChannel)
            .forEach(req -> {
              connection.send(req);
              await(1);
            });
    request.jsonParser = (jsonWrapper) -> {
      String ch = jsonWrapper.getString("ch");
      ChannelParser parser = new ChannelParser(ch);
      TradeEvent tradeEvent = new TradeEvent();
      tradeEvent.setSymbol(parser.getSymbol());
      tradeEvent.setTimestamp(TimeService.convertCSTInMillisecondToUTC(jsonWrapper.getLong("ts")));
      JsonWrapper tick = jsonWrapper.getJsonObject("tick");
      JsonWrapperArray dataArray = tick.getJsonArray("data");
      List<Trade> trades = new LinkedList<>();
      dataArray.forEach((item) -> {
        Trade trade = new Trade();
        trade.setAmount(item.getBigDecimal("amount"));
        trade.setPrice(item.getBigDecimal("price"));
        trade.setTradeId(item.getString("id"));
        trade.setDirection(TradeDirection.lookup(item.getString("direction")));
        trade.setTimestamp(TimeService.convertCSTInMillisecondToUTC(item.getLong("ts")));
        trades.add(trade);
      });
      tradeEvent.setTradeList(trades);
      return tradeEvent;
    };
    return request;
  }

  WebsocketRequest<TradeEvent> requestTradeEvent(
      List<String> symbols,
      SubscriptionListener<TradeEvent> subscriptionListener,
      SubscriptionErrorHandler errorHandler) {
    InputChecker.checker().checkSymbolList(symbols).shouldNotNull(subscriptionListener, "listener");
    WebsocketRequest<TradeEvent> request =
        new WebsocketRequest<>(subscriptionListener, errorHandler);
    if (symbols.size() == 1) {
      request.name = "Trade Req for " + symbols;
    } else {
      request.name = "Trade Req for " + symbols + " ...";
    }
    request.connectionHandler = (connection) ->
        symbols.forEach(symbol -> {
          String req = Channels.tradeChannel(Channels.OP_REQ, symbol);
          connection.send(req);
          await(1);
        });
    request.jsonParser = (jsonWrapper) -> {
      String ch = jsonWrapper.getString("rep");
      ChannelParser parser = new ChannelParser(ch);
      TradeEvent tradeEvent = new TradeEvent();
      tradeEvent.setSymbol(parser.getSymbol());
      JsonWrapperArray dataArray = jsonWrapper.getJsonArray("data");
      List<Trade> trades = new LinkedList<>();
      dataArray.forEach((item) -> {
        Trade trade = new Trade();
        trade.setAmount(item.getBigDecimal("amount"));
        trade.setPrice(item.getBigDecimal("price"));
        trade.setTradeId(item.getString("id"));
        trade.setDirection(TradeDirection.lookup(item.getString("direction")));
        trade.setTimestamp(TimeService.convertCSTInMillisecondToUTC(item.getLong("ts")));
        trades.add(trade);
      });
      tradeEvent.setTradeList(trades);
      return tradeEvent;
    };
    return request;
  }


  WebsocketRequest<PriceDepthEvent> requestPriceDepthEvent(
      List<String> symbols, DepthStep step,
      SubscriptionListener<PriceDepthEvent> subscriptionListener,
      SubscriptionErrorHandler errorHandler) {
    InputChecker.checker().checkSymbolList(symbols).shouldNotNull(subscriptionListener, "listener");

    WebsocketRequest<PriceDepthEvent> request =
        new WebsocketRequest<>(subscriptionListener, errorHandler);
    if (symbols.size() == 1) {
      request.name = "PriceDepth Req for " + symbols;
    } else {
      request.name = "PriceDepth Req  for " + symbols + " ...";
    }
    request.connectionHandler = (connection) ->
        symbols.forEach(symbol -> {
          String req = Channels.priceDepthChannel(Channels.OP_REQ, symbol, step);
          connection.send(req);
          await(1);
        });
    request.jsonParser = (jsonWrapper) -> {
      String ch = jsonWrapper.getString("rep");
      ChannelParser parser = new ChannelParser(ch);
      PriceDepthEvent priceDepthEvent = new PriceDepthEvent();
      priceDepthEvent.setTimestamp(
          TimeService.convertCSTInMillisecondToUTC(jsonWrapper.getLong("ts")));
      priceDepthEvent.setSymbol(parser.getSymbol());
      PriceDepth priceDepth = new PriceDepth();
      JsonWrapper tick = jsonWrapper.getJsonObject("data");
      priceDepth.setTimestamp(TimeService.convertCSTInMillisecondToUTC(tick.getLong("ts")));
      List<DepthEntry> bidList = new LinkedList<>();
      JsonWrapperArray bids = tick.getJsonArray("bids");
      bids.forEachAsArray((item) -> {
        DepthEntry depthEntry = new DepthEntry();
        depthEntry.setPrice(item.getBigDecimalAt(0));
        depthEntry.setAmount(item.getBigDecimalAt(1));
        bidList.add(depthEntry);
      });
      List<DepthEntry> askList = new LinkedList<>();
      JsonWrapperArray asks = tick.getJsonArray("asks");
      asks.forEachAsArray((item) -> {
        DepthEntry depthEntry = new DepthEntry();
        depthEntry.setPrice(item.getBigDecimalAt(0));
        depthEntry.setAmount(item.getBigDecimalAt(1));
        askList.add(depthEntry);
      });
      priceDepth.setAsks(askList);
      priceDepth.setBids(bidList);
      priceDepthEvent.setData(priceDepth);
      return priceDepthEvent;
    };
    return request;
  }

  WebsocketRequest<PriceDepthEvent> subscribePriceDepthEvent(
      List<String> symbols,
      SubscriptionListener<PriceDepthEvent> subscriptionListener,
      SubscriptionErrorHandler errorHandler) {
    return subscribePriceDepthEvent(symbols, DepthStep.STEP0, subscriptionListener, errorHandler);
  }

  WebsocketRequest<PriceDepthEvent> subscribePriceDepthEvent(
      List<String> symbols, DepthStep step,
      SubscriptionListener<PriceDepthEvent> subscriptionListener,
      SubscriptionErrorHandler errorHandler) {

    InputChecker.checker().checkSymbolList(symbols).shouldNotNull(subscriptionListener, "listener");

    WebsocketRequest<PriceDepthEvent> request =
        new WebsocketRequest<>(subscriptionListener, errorHandler);
    if (symbols.size() == 1) {
      request.name = "PriceDepth for " + symbols;
    } else {
      request.name = "PriceDepth for " + symbols + " ...";
    }
    request.connectionHandler = (connection) ->
        symbols.forEach(symbol -> {
          String req = Channels.priceDepthChannel(symbol, step);
          connection.send(req);
          await(1);
        });
    request.jsonParser = (jsonWrapper) -> {
      String ch = jsonWrapper.getString("ch");
      ChannelParser parser = new ChannelParser(ch);
      PriceDepthEvent priceDepthEvent = new PriceDepthEvent();
      priceDepthEvent.setTimestamp(
          TimeService.convertCSTInMillisecondToUTC(jsonWrapper.getLong("ts")));
      priceDepthEvent.setSymbol(parser.getSymbol());
      PriceDepth priceDepth = new PriceDepth();
      JsonWrapper tick = jsonWrapper.getJsonObject("tick");
      priceDepth.setTimestamp(TimeService.convertCSTInMillisecondToUTC(tick.getLong("ts")));
      List<DepthEntry> bidList = new LinkedList<>();
      JsonWrapperArray bids = tick.getJsonArray("bids");
      bids.forEachAsArray((item) -> {
        DepthEntry depthEntry = new DepthEntry();
        depthEntry.setPrice(item.getBigDecimalAt(0));
        depthEntry.setAmount(item.getBigDecimalAt(1));
        bidList.add(depthEntry);
      });
      List<DepthEntry> askList = new LinkedList<>();
      JsonWrapperArray asks = tick.getJsonArray("asks");
      asks.forEachAsArray((item) -> {
        DepthEntry depthEntry = new DepthEntry();
        depthEntry.setPrice(item.getBigDecimalAt(0));
        depthEntry.setAmount(item.getBigDecimalAt(1));
        askList.add(depthEntry);
      });
      priceDepth.setAsks(askList);
      priceDepth.setBids(bidList);
      priceDepthEvent.setData(priceDepth);
      return priceDepthEvent;
    };
    return request;
  }

  WebsocketRequest<OrderUpdateEvent> subscribeOrderUpdateEvent(
      List<String> symbols,
      SubscriptionListener<OrderUpdateEvent> subscriptionListener,
      SubscriptionErrorHandler errorHandler) {
    InputChecker.checker().checkSymbolList(symbols).shouldNotNull(subscriptionListener, "listener");
    WebsocketRequest<OrderUpdateEvent> request =
        new WebsocketRequest<>(subscriptionListener, errorHandler);
    if (symbols.size() == 1) {
      request.name = "OrderUpdate for " + symbols;
    } else {
      request.name = "OrderUpdate for " + symbols + " ...";
    }
    request.authHandler = (connection) ->
        symbols.stream()
            .map(Channels::ordersChannel)
            .forEach(req -> {
              connection.send(req);
              await(1);
            });
    request.jsonParser = (jsonWrapper) -> {
      String ch = jsonWrapper.getString("topic");
      ChannelParser parser = new ChannelParser(ch);
      OrderUpdateEvent orderUpdateEvent = new OrderUpdateEvent();
      orderUpdateEvent.setSymbol(parser.getSymbol());
      orderUpdateEvent.setTimestamp(
          TimeService.convertCSTInMillisecondToUTC(jsonWrapper.getLong("ts")));
      JsonWrapper data = jsonWrapper.getJsonObject("data");
      Order order = new Order();
      order.setOrderId(data.getLong("order-id"));
      order.setSymbol(parser.getSymbol());
      order
          .setAccountType(AccountsInfoMap.getAccount(apiKey, data.getLong("account-id")).getType());
      order.setAmount(data.getBigDecimal("order-amount"));
      order.setPrice(data.getBigDecimal("order-price"));
      order.setCreatedTimestamp(
          TimeService.convertCSTInMillisecondToUTC(data.getLong("created-at")));
      order.setType(OrderType.lookup(data.getString("order-type")));
      order.setFilledAmount(data.getBigDecimal("filled-amount"));
      order.setFilledCashAmount(data.getBigDecimal("filled-cash-amount"));
      order.setFilledFees(data.getBigDecimal("filled-fees"));
      order.setState(OrderState.lookup(data.getString("order-state")));
      order.setSource(OrderSource.lookup(data.getString("order-source")));
      orderUpdateEvent.setData(order);
      return orderUpdateEvent;
    };
    return request;
  }

  WebsocketRequest<OrderUpdateNewEvent> subscribeOrderUpdateNewEvent(
      List<String> symbols,
      SubscriptionListener<OrderUpdateNewEvent> subscriptionListener,
      SubscriptionErrorHandler errorHandler) {
    InputChecker.checker().checkSymbolList(symbols).shouldNotNull(subscriptionListener, "listener");
    WebsocketRequest<OrderUpdateNewEvent> request =
        new WebsocketRequest<>(subscriptionListener, errorHandler);
    if (symbols.size() == 1) {
      request.name = "OrderUpdateNew for " + symbols;
    } else {
      request.name = "OrderUpdateNew for " + symbols + " ...";
    }
    request.authHandler = (connection) ->
        symbols.stream()
            .map(Channels::ordersChannelNew)
            .forEach(req -> {
              connection.send(req);
              await(1);
            });
    request.jsonParser = (jsonWrapper) -> {
      OrderUpdateNewEvent orderUpdateEvent = new OrderUpdateNewEvent();
      JsonWrapper data = jsonWrapper.getJsonObject("data");
      String symbol = data.getString("symbol");
      orderUpdateEvent.setSymbol(symbol);
      orderUpdateEvent.setTimestamp(TimeService.convertCSTInMillisecondToUTC(jsonWrapper.getLong("ts")));

      orderUpdateEvent.setData(OrderUpdate.builder()
          .matchId(data.getLong("match-id"))
          .orderId(data.getLong("order-id"))
          .symbol(symbol)
          .state(OrderState.lookup(data.getString("order-state")))
          .type(OrderType.lookup(data.getString("order-type")))
          .role(DealRole.find(data.getString("role")))
          .price(data.getBigDecimal("price"))
          .filledAmount(data.getBigDecimal("filled-amount"))
          .filledCashAmount(data.getBigDecimal("filled-cash-amount"))
          .unfilledAmount(data.getBigDecimal("unfilled-amount"))
          .clientOrderId(data.getStringOrDefault("client-order-id", null))
          .build());
      return orderUpdateEvent;
    };
    return request;
  }

  WebsocketRequest<AccountEvent> subscribeAccountEvent(
      BalanceMode mode,
      SubscriptionListener<AccountEvent> subscriptionListener,
      SubscriptionErrorHandler errorHandler) {
    InputChecker.checker().shouldNotNull(subscriptionListener, "listener");
    WebsocketRequest<AccountEvent> request =
        new WebsocketRequest<>(subscriptionListener, errorHandler);
    request.name = "Account";
    request.authHandler = (connection) ->
        connection.send(Channels.accountChannel(mode));
    request.jsonParser = (jsonWrapper) -> {
      AccountEvent accountEvent = new AccountEvent();
      accountEvent.setTimestamp(
          TimeService.convertCSTInMillisecondToUTC(jsonWrapper.getLong("ts")));
      JsonWrapper data = jsonWrapper.getJsonObject("data");
      accountEvent.setChangeType(AccountChangeType.lookup(data.getString("event")));
      JsonWrapperArray listArray = data.getJsonArray("list");
      List<AccountChange> accountChangeList = new LinkedList<>();
      listArray.forEach((itemInList) -> {
        AccountChange change = new AccountChange();
        change.setAccountType(
            AccountsInfoMap.getAccount(apiKey, itemInList.getLong("account-id")).getType());
        change.setCurrency(itemInList.getString("currency"));
        change.setBalance(itemInList.getBigDecimal("balance"));
        change.setBalanceType(BalanceType.lookup(itemInList.getString("type")));
        accountChangeList.add(change);
      });
      accountEvent.setData(accountChangeList);
      return accountEvent;
    };
    return request;
  }

  WebsocketRequest<TradeStatisticsEvent> subscribe24HTradeStatisticsEvent(
      List<String> symbols,
      SubscriptionListener<TradeStatisticsEvent> subscriptionListener,
      SubscriptionErrorHandler errorHandler) {
    InputChecker.checker().checkSymbolList(symbols).shouldNotNull(subscriptionListener, "listener");
    WebsocketRequest<TradeStatisticsEvent> request =
        new WebsocketRequest<>(subscriptionListener, errorHandler);
    if (symbols.size() == 1) {
      request.name = "24HTradeStatistics for " + symbols;
    } else {
      request.name = "24HTradeStatistics for " + symbols + " ...";
    }
    request.connectionHandler = (connection) -> {
      for (String symbol : symbols) {
        symbol = Channels.tradeStatisticsChannel(symbol);
        connection.send(symbol);
      }
    };
    request.jsonParser = (jsonWrapper) -> {
      String ch = jsonWrapper.getString("ch");
      ChannelParser parser = new ChannelParser(ch);
      TradeStatisticsEvent tradeStatisticsEvent = new TradeStatisticsEvent();
      tradeStatisticsEvent.setSymbol(parser.getSymbol());
      JsonWrapper tick = jsonWrapper.getJsonObject("tick");
      long ts = TimeService.convertCSTInMillisecondToUTC(jsonWrapper.getLong("ts"));
      tradeStatisticsEvent.setTimeStamp(ts);
      TradeStatistics statistics = new TradeStatistics();
      statistics.setAmount(tick.getBigDecimal("amount"));
      statistics.setOpen(tick.getBigDecimal("open"));
      statistics.setClose(tick.getBigDecimal("close"));
      statistics.setHigh(tick.getBigDecimal("high"));
      statistics.setTimestamp(ts);
      //statistics.setId(tick.getLong("id"));
      statistics.setCount(tick.getLong("count"));
      statistics.setLow(tick.getBigDecimal("low"));
      statistics.setVolume(tick.getBigDecimal("vol"));
      tradeStatisticsEvent.setData(statistics);
      return tradeStatisticsEvent;
    };
    return request;
  }

  WebsocketRequest<TradeStatisticsEvent> request24HTradeStatisticsEvent(
      List<String> symbols,
      SubscriptionListener<TradeStatisticsEvent> subscriptionListener,
      SubscriptionErrorHandler errorHandler) {
    InputChecker.checker().checkSymbolList(symbols).shouldNotNull(subscriptionListener, "listener");
    WebsocketRequest<TradeStatisticsEvent> request =
        new WebsocketRequest<>(subscriptionListener, errorHandler);
    if (symbols.size() == 1) {
      request.name = "24HTradeStatistics Req for " + symbols;
    } else {
      request.name = "24HTradeStatistics Req for " + symbols + " ...";
    }
    request.connectionHandler = (connection) -> {
      for (String symbol : symbols) {
        String req = Channels.tradeStatisticsChannel(Channels.OP_REQ,symbol);
        connection.send(req);
      }
    };
    request.jsonParser = (jsonWrapper) -> {
      String ch = jsonWrapper.getString("rep");
      ChannelParser parser = new ChannelParser(ch);
      TradeStatisticsEvent tradeStatisticsEvent = new TradeStatisticsEvent();
      tradeStatisticsEvent.setSymbol(parser.getSymbol());
      JsonWrapper tick = jsonWrapper.getJsonObject("data");
      long ts = TimeService.convertCSTInMillisecondToUTC(jsonWrapper.getLong("ts"));
      tradeStatisticsEvent.setTimeStamp(ts);
      TradeStatistics statistics = new TradeStatistics();
      statistics.setAmount(tick.getBigDecimal("amount"));
      statistics.setOpen(tick.getBigDecimal("open"));
      statistics.setClose(tick.getBigDecimal("close"));
      statistics.setHigh(tick.getBigDecimal("high"));
      statistics.setTimestamp(ts);
      //statistics.setId(tick.getLong("id"));
      statistics.setCount(tick.getLong("count"));
      statistics.setLow(tick.getBigDecimal("low"));
      statistics.setVolume(tick.getBigDecimal("vol"));
      tradeStatisticsEvent.setData(statistics);
      return tradeStatisticsEvent;
    };
    return request;
  }

  WebsocketRequest<AccountListEvent> requestAccountListEvent(
      SubscriptionListener<AccountListEvent> subscriptionListener,
      SubscriptionErrorHandler errorHandler){

    WebsocketRequest<AccountListEvent> request = new WebsocketRequest<AccountListEvent>(subscriptionListener,errorHandler);
    request.authHandler = (connection) ->{
      connection.send(Channels.requestAccountListChannel());
    };
    request.jsonParser = (jsonWrapper) -> {
      long ts = TimeService.convertCSTInMillisecondToUTC(jsonWrapper.getLong("ts"));
      JsonWrapperArray array = jsonWrapper.getJsonArray("data");
      List<Account> accountList = new ArrayList<>();
      array.forEach(accountItem ->{
        Account account = new Account();
        account.setId(accountItem.getLong("id"));
        account.setType(AccountType.lookup(accountItem.getString("type")));
        account.setState(AccountState.lookup(accountItem.getString("state")));
        List<Balance> balanceList = new ArrayList<>();
        JsonWrapperArray balanceArray = accountItem.getJsonArray("list");
        balanceArray.forEach(balanceItem ->{
          Balance balance = new Balance();
          balance.setBalance(balanceItem.getBigDecimal("balance"));
          balance.setCurrency(balanceItem.getString("currency"));
          balance.setType(BalanceType.lookup(balanceItem.getString("type")));
          balanceList.add(balance);
        });

        account.setBalances(balanceList);
        accountList.add(account);
      });

      return AccountListEvent.builder()
          .timestamp(ts)
          .accountList(accountList)
          .build();
    };

    return request;
  }
}
