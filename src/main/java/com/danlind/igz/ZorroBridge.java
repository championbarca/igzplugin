package com.danlind.igz;

import com.danlind.igz.config.ZorroReturnValues;
import com.danlind.igz.domain.types.Epic;
import com.danlind.igz.domain.types.OrderText;
import com.danlind.igz.handler.*;
import com.danlind.igz.misc.TimeConvert;
import io.reactivex.exceptions.UndeliverableException;
import io.reactivex.plugins.RxJavaPlugins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.SocketException;


public class ZorroBridge {

    static {
        System.setProperty("logging.config", "./Plugin/ig/logback.xml");
        System.setProperty("spring.config.location", "file:./Plugin/ig/application.properties");
    }
    final ConfigurableApplicationContext context;

    private AccountHandler accountHandler;
    private HistoryHandler historyHandler;
    private TradeHandler tradeHandler;
    private TimeHandler timeHandler;
    private AssetHandler assetHandler;
    private final LoginHandler loginHandler;

    private boolean isFirstLogin = true;

    private final static Logger logger = LoggerFactory.getLogger(ZorroBridge.class);

    public ZorroBridge() {
        context = SpringApplication.run(IgzApplication.class);
        loginHandler = context.getBean(LoginHandler.class);
    }

    private void initComponents() {
        setRxErrorHandler();
        timeHandler = context.getBean(TimeHandler.class);
        timeHandler.subscribeToLighstreamerHeartbeat();
        assetHandler = context.getBean(AssetHandler.class);
        historyHandler = context.getBean(HistoryHandler.class);
        accountHandler = context.getBean(AccountHandler.class);
        accountHandler.startAccountSubscription();
        tradeHandler = context.getBean(TradeHandler.class);
        tradeHandler.checkTradesValid();
        historyHandler.startTimeZoneOffsetSubscription();

        if (!isFirstLogin) {
            logger.debug("Zorro requested new login, resubscribing to all assets");
            assetHandler.reconnectAll();
        }

        logger.debug("Initialization complete");
    }

    public int doLogin(final String User,
                       final String Pwd,
                       final String Type,
                       final String Accounts[]) {
        logger.info("Broker Login called with User {}, Type {}, pwd {}", User, Type, Pwd);
        final int loginResult = loginHandler.connect(User,
                                                         Pwd,
                                                         Type);
        if (loginResult == ZorroReturnValues.LOGIN_OK.getValue()) {
            logger.info("Login successful");
            initComponents();
            isFirstLogin = false;
        }

        return loginResult;
    }

    public int doLoginV2(final String User,
                       final String Pwd,
                       final String Type,
                       final String Accounts[]) {
        logger.info("Broker Login called with User {}, Type {}, pwd {}", User, Type, Pwd);
        final int loginResult = loginHandler.connectV2(User,
                                                         Pwd,
                                                         Type);
        if (loginResult == ZorroReturnValues.LOGIN_OK.getValue()) {
            logger.info("Login successful");
            initComponents();
            isFirstLogin = false;
        }

        return loginResult;
    }

    public int doLogout() {
        logger.debug("Broker Logout called");
        historyHandler.cancelSubscription();
        return loginHandler.disconnect();
    }

    public int doBrokerTime(final double pTimeUTC[]) {
        return timeHandler.getBrokerTime(pTimeUTC);
    }

    public int doSubscribeAsset(final String Asset) {
        logger.debug("Broker Subscribe called with params \nAsset {}", Asset);
        return assetHandler.subscribeToLighstreamerTickUpdates(new Epic(Asset));
    }

    public int doBrokerAsset(final String Asset,
                             final double assetParams[]) {
//        Logging BrokerAsset calls will create A LOT of log output
//        logger.debug("Broker Asset called with params \nAsset {}", Asset);
        return assetHandler.getLatestAssetData(new Epic(Asset), assetParams);
    }

    public int doBrokerAccount(final double accountInfoParams[]) {
//        Logging BrokerAccount calls will produce A LOT of log output
//        logger.debug("Broker Account called");
        return accountHandler.brokerAccount(accountInfoParams);
    }

    public int doBrokerTrade(final int nTradeID,
                             final double orderParams[]) {
//        logger.debug("Broker Trade called with params \nnTradeId {}", nTradeID);
        return tradeHandler.brokerTrade(nTradeID, orderParams);
    }

    public int doBrokerBuy(final String Asset,
                           final double tradeParams[]) {
        logger.debug("Broker Buy called with params \nAsset {}, \nnoOfContracts {}, \nstopDistance {}",
                Asset,
                tradeParams[0],
                tradeParams[1]);
        return tradeHandler.brokerBuy(new Epic(Asset), tradeParams);
    }

    public int doBrokerSell(final int nTradeID,
                            final int nAmount) {
        logger.debug("Broker Sell called with params \nnTradeId {}, \nnAmount {}",
                nTradeID,
                nAmount);
        return tradeHandler.brokerSell(nTradeID, nAmount);
    }

    public int doBrokerStop(final int nTradeID,
                            final double dStop) {
        logger.debug("Broker Stop called with params \nnTradeId {}, \ndStop {}",
                nTradeID,
                dStop);
        return tradeHandler.brokerStop(nTradeID, dStop);
    }

    public int doBrokerHistory2(final String Asset,
                                final double tStart,
                                final double tEnd,
                                final int nTickMinutes,
                                final int nTicks,
                                final double tickParams[]) {
        logger.debug("Broker history called with params \nEpic {}, \ntStart {}, \ntEnd {}, \nnTickMinutes {}, \nnTicks {}",
                Asset,
                TimeConvert.dateTimeFromOLEDate(tStart),
                TimeConvert.dateTimeFromOLEDate(tEnd),
                nTickMinutes,
                nTicks);

        return historyHandler.getPriceHistory(new Epic(Asset),
                tStart,
                tEnd,
                nTickMinutes,
                nTicks,
                tickParams);
    }

    public int doSetOrderText(final String orderText) {
        return tradeHandler.setOrderText(new OrderText(orderText));
    }

    private void setRxErrorHandler() {
        RxJavaPlugins.setErrorHandler(e -> {
            if (e instanceof UndeliverableException) {
                e = e.getCause();
            }
            if ((e instanceof IOException) || (e instanceof SocketException)) {
                // fine, irrelevant network problem or API that throws on cancellation
                return;
            }
            if (e instanceof InterruptedException) {
                // fine, some blocking code was interrupted by a dispose call
                return;
            }
            if ((e instanceof NullPointerException) || (e instanceof IllegalArgumentException)) {
                // that's likely a bug in the application
                Thread.currentThread().getUncaughtExceptionHandler()
                    .uncaughtException(Thread.currentThread(), e);
                return;
            }
            if (e instanceof IllegalStateException) {
                // that's a bug in RxJava or in a custom operator
                Thread.currentThread().getUncaughtExceptionHandler()
                    .uncaughtException(Thread.currentThread(), e);
                return;
            }
            logger.warn("Undeliverable exception received, not sure what to do", e);
        });
    }
}
