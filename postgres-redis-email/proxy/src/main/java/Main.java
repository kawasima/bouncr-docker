import enkan.Env;
import enkan.component.jedis.JedisProvider;
import enkan.system.EnkanSystem;
import net.unit8.bouncr.component.BouncrConfiguration;
import net.unit8.bouncr.proxy.BouncrProxyEnkanSystemFactory;

import static enkan.component.ComponentRelationship.component;
import static enkan.util.BeanBuilder.builder;

public class Main {
    public static void main(String[] args) {
        EnkanSystem system = new BouncrProxyEnkanSystemFactory().create();
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

        Runtime.getRuntime().addShutdownHook(new Thread(
                system::stop
        ));
        system.start();
    }

}
