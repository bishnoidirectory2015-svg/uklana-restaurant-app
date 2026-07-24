package com.uklanafood.restaurant;

public final class AppConfig {
    private AppConfig() {}
    public static final String API_BASE = "https://uklana.food/wp-json/ukf-restaurant/v1";
    public static final String LOGIN_URL = API_BASE + "/login";
    public static final String DASHBOARD_URL = API_BASE + "/dashboard";
    public static final String ORDERS_URL = API_BASE + "/orders";
    public static final String SET_OPEN_URL = API_BASE + "/set-open";
    public static final String DONE_URL = API_BASE + "/done-order";
    public static final String RESET_URL = API_BASE + "/reset-total";
    public static final String CHANNEL_ID = "uklana_restaurant_orders_v1";
    public static final long REFRESH_MS = 15000L;
}
