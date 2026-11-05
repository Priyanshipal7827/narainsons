package com.narainsons.config;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;
import com.narainsons.service.EmiReminderService;
import com.narainsons.service.PushService;
import com.narainsons.util.DBConnection;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
/** Registered in WEB-INF/web.xml only (avoid duplicate @WebListener scan issues). */
public class AppShutdownListener implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(AppShutdownListener.class.getName());

    private ScheduledExecutorService scheduler;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        if (AppConfig.isReminderSchedulerEnabled()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "emi-reminders");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleAtFixedRate(AppShutdownListener::runReminders, 5, 60, TimeUnit.MINUTES);
        }
    }

    private static void runReminders() {
        try {
            LOG.info("EMI reminders: " + new EmiReminderService().runHourlyReminders());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "EMI reminder run failed", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        PushService.shared().shutdown();
        try {
            DBConnection.shutdown();
        } catch (Throwable e) {
            LOG.log(Level.FINE, "Connection pool shutdown", e);
        }
        try {
            AbandonedConnectionCleanupThread.checkedShutdown();
        } catch (Exception e) {
            LOG.log(Level.FINE, "MySQL cleanup thread shutdown", e);
        }

        ClassLoader appLoader = sce.getServletContext().getClassLoader();
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (driver.getClass().getClassLoader() == appLoader) {
                try {
                    DriverManager.deregisterDriver(driver);
                } catch (SQLException e) {
                    LOG.log(Level.FINE, "JDBC driver deregister", e);
                }
            }
        }
    }
}
