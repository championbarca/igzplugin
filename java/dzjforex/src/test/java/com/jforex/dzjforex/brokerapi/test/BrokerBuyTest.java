package com.jforex.dzjforex.brokerapi.test;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.jforex.dzjforex.brokerapi.BrokerBuy;
import com.jforex.dzjforex.config.Constant;
import com.jforex.dzjforex.order.SubmitHandler;
import com.jforex.dzjforex.order.TradeUtil;
import com.jforex.dzjforex.test.util.CommonUtilForTest;

import de.bechte.junit.runners.context.HierarchicalContextRunner;

@RunWith(HierarchicalContextRunner.class)
public class BrokerBuyTest extends CommonUtilForTest {

    private BrokerBuy brokerBuy;

    @Mock
    private SubmitHandler submitHandlerMock;
    @Mock
    private TradeUtil tradeUtilMock;
    @Mock
    private IOrder orderMock;
    private final Instrument buyInstrument = Instrument.EURUSD;
    private final OrderCommand command = OrderCommand.BUY;
    private final int nAmount = 12500;
    private final double tradeParams[] = new double[3];

    @Before
    public void setUp() {
        tradeParams[0] = nAmount;

        brokerBuy = new BrokerBuy(submitHandlerMock, tradeUtilMock);
    }

    @Test
    public void anInvalidAssetNameGivesBrokerBuyFail() {
        when(tradeUtilMock.maybeInstrumentForTrading("Invalid"))
            .thenReturn(Optional.empty());

        assertThat(brokerBuy.openTrade("Invalid", tradeParams),
                   equalTo(Constant.BROKER_BUY_FAIL));
    }

    public class WhenAssetNameIsValid {

        private final String assetName = "EUR/USD";
        private int returnCode;
        private final int orderID = 1234;
        private final double tradeAmount = 0.0125;
        private final double dStopDist = 0.00023;
        private final double slPrice = 1.32456;
        private final String orderLabelPrefix = "Zorro";
        private final String orderLabel = orderLabelPrefix + orderID;

        private void setupSubmitHandlerOrder(final IOrder order,
                                             final double slPrice) {
            when(submitHandlerMock.submit(orderID,
                                          buyInstrument,
                                          command,
                                          tradeAmount,
                                          orderLabel,
                                          slPrice))
                                              .thenReturn(order);
        }

        @Before
        public void setUp() {
            when(tradeUtilMock.maybeInstrumentForTrading(assetName))
                .thenReturn(Optional.of(buyInstrument));
            when(tradeUtilMock.createOrderID())
                .thenReturn(orderID);
            when(tradeUtilMock.contractsToAmount(nAmount))
                .thenReturn(tradeAmount);
            when(tradeUtilMock.orderCommandForContracts(nAmount))
                .thenReturn(command);
            when(tradeUtilMock.orderLabelPrefix())
                .thenReturn(orderLabelPrefix);
            when(tradeUtilMock.calculateSL(buyInstrument,
                                           command,
                                           dStopDist))
                                               .thenReturn(slPrice);
        }

        public class WhenOrderIsValid {

            private final double openPrice = 1.32665;

            @Before
            public void setUp() {
                when(orderMock.getOpenPrice()).thenReturn(openPrice);
            }

            public class WhendStopDistIsNegative {

                @Before
                public void setUp() {
                    setupSubmitHandlerOrder(orderMock, 0.0);

                    tradeParams[1] = -1;

                    returnCode = brokerBuy.openTrade(assetName, tradeParams);
                }

                @Test
                public void returnCodeIsBuyOpposite() {
                    assertThat(returnCode, equalTo(Constant.BROKER_BUY_OPPOSITE_CLOSE));
                }

                @Test
                public void openPriceIsStoredAtTradeparams() {
                    assertThat(tradeParams[2], equalTo(openPrice));
                }

                @Test
                public void callToSubmitHandlerIsCorrectWithNoSL() {
                    verify(submitHandlerMock).submit(orderID,
                                                     buyInstrument,
                                                     command,
                                                     tradeAmount,
                                                     orderLabel,
                                                     0.0);
                }
            }

            public class WhendStopDistIsZero {

                @Before
                public void setUp() {
                    setupSubmitHandlerOrder(orderMock, 0.0);

                    tradeParams[1] = 0.0;

                    returnCode = brokerBuy.openTrade(assetName, tradeParams);
                }

                @Test
                public void returnCodeIsOrderID() {
                    assertThat(returnCode, equalTo(orderID));
                }

                @Test
                public void openPriceIsStoredAtTradeparams() {
                    assertThat(tradeParams[2], equalTo(openPrice));
                }

                @Test
                public void callToSubmitHandlerIsCorrectWithNoSL() {
                    verify(submitHandlerMock).submit(orderID,
                                                     buyInstrument,
                                                     command,
                                                     tradeAmount,
                                                     orderLabel,
                                                     0.0);
                }
            }

            public class WhendStopDistIsPositive {

                @Before
                public void setUp() {
                    setupSubmitHandlerOrder(orderMock, slPrice);

                    tradeParams[1] = dStopDist;

                    returnCode = brokerBuy.openTrade(assetName, tradeParams);
                }

                @Test
                public void returnCodeIsOrderID() {
                    assertThat(returnCode, equalTo(orderID));
                }

                @Test
                public void openPriceIsStoredAtTradeparams() {
                    assertThat(tradeParams[2], equalTo(openPrice));
                }

                @Test
                public void callToSubmitHandlerIsCorrect() {
                    verify(submitHandlerMock).submit(orderID,
                                                     buyInstrument,
                                                     command,
                                                     tradeAmount,
                                                     orderLabel,
                                                     slPrice);
                }
            }
        }

        public class WhenOrderIsInValid {

            @Before
            public void setUp() {
                setupSubmitHandlerOrder(null, slPrice);

                tradeParams[1] = dStopDist;

                returnCode = brokerBuy.openTrade(assetName, tradeParams);
            }

            @Test
            public void returnCodeIsBrokerBuyFail() {
                assertThat(returnCode, equalTo(Constant.BROKER_BUY_FAIL));
            }

            @Test
            public void openPriceIsNotStoredAtTradeparams() {
                assertThat(tradeParams[2], equalTo(0.0));
            }

            @Test
            public void callToSubmitHandlerIsCorrect() {
                verify(submitHandlerMock).submit(orderID,
                                                 buyInstrument,
                                                 command,
                                                 tradeAmount,
                                                 orderLabel,
                                                 slPrice);
            }
        }
    }
}
