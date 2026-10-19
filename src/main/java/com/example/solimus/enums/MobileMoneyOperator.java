package com.example.solimus.enums;

// Opérateur choisi pour un paiement via l'API TouchPay (GU) — chaque opérateur a son code de
// service côté GU, et sa correspondance avec les modes de paiement déjà stockés en base
public enum MobileMoneyOperator {

    WAVE("SNPAIEMENTWAVE", PaymentMethod.WAVE, ChargePaymentMethod.WAVE),
    ORANGE_MONEY("PAIEMENTMARCHANDOM", PaymentMethod.ORANGE_MONEY, ChargePaymentMethod.ORANGE_MONEY);

    private final String serviceCode;
    private final PaymentMethod paymentMethod;
    private final ChargePaymentMethod chargePaymentMethod;

    MobileMoneyOperator(String serviceCode, PaymentMethod paymentMethod, ChargePaymentMethod chargePaymentMethod) {
        this.serviceCode = serviceCode;
        this.paymentMethod = paymentMethod;
        this.chargePaymentMethod = chargePaymentMethod;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public ChargePaymentMethod getChargePaymentMethod() {
        return chargePaymentMethod;
    }
}
