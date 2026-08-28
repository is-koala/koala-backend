package com.exskylab.koala.core.utilities.payment.iyzico;

public final class IyzicoEndpoints {

    public static final String SUBMERCHANT = "/onboarding/submerchant";
    public static final String CHECKOUT_FORM_INITIALIZE = "/payment/iyzipos/checkoutform/initialize/auth/ecom";
    public static final String CHECKOUT_FORM_RETRIEVE = "/payment/iyzipos/checkoutform/auth/ecom/detail";
    public static final String ITEM_APPROVE = "/payment/iyzipos/item/approve";
    public static final String ITEM_DISAPPROVE = "/payment/iyzipos/item/disapprove";
    public static final String REFUND = "/payment/refund";
    public static final String CANCEL = "/payment/cancel";

    private IyzicoEndpoints() {
    }
}
