package net.mabako.steamgifts;

public class Application extends ApplicationTemplate {
    @Override
    public void onCreate() {
        super.onCreate();
        setupFabric();
    }

    @Override
    public String getAppVersionName() {
        return BuildConfig.VERSION_NAME;
    }

    @Override
    public int getAppVersionCode() {
        return BuildConfig.VERSION_CODE;
    }
}