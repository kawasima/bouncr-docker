package config;

import enkan.Env;
import enkan.component.ApplicationComponent;
import enkan.component.eclipselink.EclipseLinkEntityManagerProvider;
import enkan.component.flyway.FlywayMigration;
import enkan.component.jedis.JedisProvider;
import enkan.component.thymeleaf.ThymeleafTemplateEngine;
import enkan.system.EnkanSystem;
import kotowari.component.TemplateEngine;
import net.unit8.bouncr.component.BouncrConfiguration;
import net.unit8.bouncr.component.config.HookPoint;
import net.unit8.bouncr.extention.app.EmailApplicationCustomizer;
import net.unit8.bouncr.hook.NormalizeMailAddressHook;
import net.unit8.bouncr.hook.SendPasswordResetChallengeHook;
import net.unit8.bouncr.hook.SendVerificationHook;
import net.unit8.bouncr.hook.email.config.MailConfig;
import net.unit8.bouncr.hook.email.config.MailMetaConfig;
import net.unit8.bouncr.hook.email.config.MailServerConfig;
import net.unit8.bouncr.hook.license.LicenseCheckHook;
import net.unit8.bouncr.hook.license.LicenseConfig;
import net.unit8.bouncr.hook.license.LicenseDeleteHook;
import net.unit8.bouncr.hook.license.LicenseUpdateHook;
import net.unit8.bouncr.hook.license.entity.LicenseLastActivity;
import net.unit8.bouncr.hook.license.entity.UserLicense;

import java.net.URI;
import java.util.Objects;

import static enkan.component.ComponentRelationship.component;
import static enkan.util.BeanBuilder.builder;

public class SystemCustomizer {
    public static void customize(EnkanSystem system) {
        BouncrConfiguration config = system.getComponent("config");
        JedisProvider provider = builder(new JedisProvider())
                .set(JedisProvider::setHost, Env.getString("REDIS_HOST", "localhost"))
                .build();
        system.setComponent("jedis", provider);

        config.getKeyValueStoreSettings().setAccessTokenStoreFactory(deps ->
                provider.createStore("access-token", 1800));
        config.getKeyValueStoreSettings().setAuthorizationCodeStoreFactory(deps ->
                provider.createStore("authorization-code", 1800));
        config.getKeyValueStoreSettings().setBouncrTokenStoreFactory(deps ->
                provider.createStore("bouncr-token", 3600 * 12));
        config.getKeyValueStoreSettings().setOidcSessionStoreFactory(deps ->
                provider.createStore("oidc-session", 300));
        system.relationships(component("config").using("jedis"));

        config.getOidcConfiguration().setSignUpRedirectUrl(URI.create("http://localhost:8000/#/sign_up"));
        config.getOidcConfiguration().setSignInRedirectUrl(URI.create("http://localhost:8000/#/sign_in_by_oidc"));

        config.getHookRepo().register(HookPoint.BEFORE_VALIDATE_USER_PROFILES, new NormalizeMailAddressHook());
        MailServerConfig mailServerConfig = builder(new MailServerConfig())
                .set(MailServerConfig::setSmtpHost, Env.get("MAIL_HOST"))
                .set(MailServerConfig::setSmtpPort, Env.getInt("MAIL_PORT", 25))
                .set(MailServerConfig::setSmtpUsername, Env.get("MAIL_USER"))
                .set(MailServerConfig::setSmtpPassword, Env.get("MAIL_PASSWORD"))
                .set(MailServerConfig::setSmtpAuth, true)
                .build();
        TemplateEngine templateEngine = builder(new ThymeleafTemplateEngine())
                .set(ThymeleafTemplateEngine::setPrefix, "/templates/mail/")
                .set(ThymeleafTemplateEngine::setSuffix, ".txt")
                .build();
        system.setComponent("template", templateEngine);

        MailConfig mailConfig = builder(new MailConfig())
                .set(MailConfig::setMailServerConfig, mailServerConfig)
                .set(MailConfig::setTemplateEngine, templateEngine)
                .build();
        system.setComponent("mailConfig", mailConfig);
        system.relationships(component("app").using("mailConfig"));
        // Application
        final ApplicationComponent app = system.getComponent("app", ApplicationComponent.class);
        app.setApplicationCustomizer(new EmailApplicationCustomizer());

        SendPasswordResetChallengeHook sendPasswordResetChallengeHook = builder(new SendPasswordResetChallengeHook())
                .set(SendPasswordResetChallengeHook::setMailConfig, mailConfig)
                .build();
        config.getHookRepo().register(HookPoint.AFTER_PASSWORD_RESET_CHALLENGE, sendPasswordResetChallengeHook);
        mailConfig.putMailMetaConfig("PasswordResetChallenge", builder(new MailMetaConfig())
                .set(MailMetaConfig::setFromAddress, Env.getString("BOUNCR_MAIL_FROM", "bouncr@example.com"))
                .set(MailMetaConfig::setFromName, Env.getString("BOUNCR_MAIL_FROM_NAME", "Bouncr Administrator"))
                .set(MailMetaConfig::setContentType, "plain")
                .set(MailMetaConfig::setSubject, "[Bouncr] Reset your password")
                .set(MailMetaConfig::setTemplateName, "password_reset_code")
                .build());

        SendVerificationHook sendVerificationHook = builder(new SendVerificationHook())
                .set(SendVerificationHook::setMailConfig, mailConfig)
                .build();
        config.getHookRepo().register(HookPoint.AFTER_SIGN_UP, sendVerificationHook);
        config.getHookRepo().register(HookPoint.AFTER_UPDATE_USER, sendVerificationHook);
        mailConfig.putMailMetaConfig("Verification", builder(new MailMetaConfig())
                .set(MailMetaConfig::setFromAddress, Env.getString("BOUNCR_MAIL_FROM", "bouncr@example.com"))
                .set(MailMetaConfig::setFromName, Env.getString("BOUNCR_MAIL_FROM_NAME", "Bouncr Administrator"))
                .set(MailMetaConfig::setContentType, "plain")
                .set(MailMetaConfig::setSubject, "[Bouncr] Mail Address Verification")
                .set(MailMetaConfig::setTemplateName, "verification")
                .build());


        LicenseConfig licenseConfig = builder(new LicenseConfig()).build();
        final EclipseLinkEntityManagerProvider jpa = system.getComponent("jpa", EclipseLinkEntityManagerProvider.class);
        jpa.registerClasses(UserLicense.class, LicenseLastActivity.class);
        system.setComponent("flyway_hook_license", builder(new FlywayMigration())
                .set(FlywayMigration::setCleanBeforeMigration, Objects.equals(Env.getString("CLEAR_SCHEMA", "false"), "true"))
                .set(FlywayMigration::setLocation, new String[]{"classpath:net/unit8/bouncr/hook/license/migration"})
                .set(FlywayMigration::setTable, "license_hook_versions")
                .build());
        system.relationships(component("flyway_hook_license").using("datasource"));
        system.relationships(component("jpa").using("datasource", "flyway", "flyway_hook_license"));
        LicenseCheckHook licenseCheckHook   = new LicenseCheckHook(licenseConfig);
        LicenseUpdateHook licenseUpdateHook = new LicenseUpdateHook(licenseConfig);
        LicenseDeleteHook licenseDeleteHook = new LicenseDeleteHook(licenseConfig);

        config.getHookRepo().register(HookPoint.ALLOWED_SIGN_IN, licenseCheckHook);
        config.getHookRepo().register(HookPoint.AFTER_SIGN_IN, licenseUpdateHook);
        config.getHookRepo().register(HookPoint.AFTER_SIGN_OUT, licenseDeleteHook);
    }
}
