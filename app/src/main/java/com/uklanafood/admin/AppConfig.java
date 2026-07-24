package com.uklanafood.admin;

public final class AppConfig {
    private AppConfig() {}
    public static final String REGISTER_URL = "https://uklana.food/wp-json/ukf-admin/v1/register-token";
    public static final String ORDERS_URL = "https://uklana.food/wp-admin/admin.php?page=wc-orders";
    public static final String APP_SECRET = "UKF-FqS2g3vYngg-se_c6lk0a7F_ov_l652S";
    public static final String DELIVERY_SECRET = "UKF-Delivery-7Kx9pQ2mN4sV8cR1";
    public static final String LOGIN_URL = "https://uklana.food/wp-json/ukf-delivery/v1/login";
    public static final String PENDING_URL = "https://uklana.food/wp-json/ukf-delivery/v1/pending-orders";
    public static final String UPDATE_URL = "https://uklana.food/wp-json/ukf-delivery/v1/update-status";
    public static final String CHANNEL_ID = "uklana_order_alerts_v1";
}
