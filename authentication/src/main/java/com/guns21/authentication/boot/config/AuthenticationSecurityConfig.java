package com.guns21.authentication.boot.config;

import com.guns21.authentication.ext.AuthExtValidator;
import com.guns21.authentication.filter.AccessFilter;
import com.guns21.authentication.security.HttpAuthenticationFailureHandler;
import com.guns21.authentication.security.HttpAuthenticationSuccessHandler;
import com.guns21.authentication.security.HttpLogoutSuccessHandler;
import com.guns21.authentication.security.PasswordEncryptAuthenticationProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.access.channel.ChannelProcessingFilter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.data.redis.RedisOperationsSessionRepository;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.*;

/**
 * 认证
 * * see https://stackoverflow.com/questions/34314084/howto-additionally-add-spring-security-captcha-filter-for-specific-urls-only
 */
@Configuration
@EnableWebSecurity
@Order(100)
//@ConfigurationProperties(prefix = "com.guns21.security") TODO 当有多处需要注入相同的prefix时不能使用ConfigurationProperties注入
public class AuthenticationSecurityConfig extends WebSecurityConfigurerAdapter {
    @Value("${com.guns21.security.login:/login}")
    private String login;
    @Value("${com.guns21.security.logout:/logout}")
    private String logout;
    @Value("${com.guns21.security.username-parameter:username}")
    private String usernameParameter;
    @Value("${com.guns21.security.password-parameter:password}")
    private String passwordParameter;

    @Autowired
    private SecurityConfig securityConfig;

    @Autowired
    private HttpLogoutSuccessHandler httpLogoutSuccessHandler;
    @Autowired
    private HttpAuthenticationFailureHandler httpAuthenticationFailureHandler;
    @Autowired
    private RedisOperationsSessionRepository redisOperationsSessionRepository;

    @Autowired
    private AuthExtValidator authExtValidator;

    @Bean
    @ConditionalOnMissingBean(name = "beforeLoginFilter")
    public Filter beforeLoginFilter() {
        return new AccessFilter();
    }

    @Bean
    @ConditionalOnMissingBean(AuthenticationSuccessHandler.class)
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return new HttpAuthenticationSuccessHandler();
    }


    @Bean
    @ConditionalOnMissingBean(name = "passwordAuthenticationProvider")
    public AuthenticationProvider passwordAuthenticationProvider() {
        return new PasswordEncryptAuthenticationProvider();
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.authenticationProvider(passwordAuthenticationProvider());
    }

    @Bean
    public SpringSessionBackedSessionRegistry springSessionBackedSessionRegistry() {
        return new SpringSessionBackedSessionRegistry((FindByIndexNameSessionRepository) this.redisOperationsSessionRepository);
    }

    /**
     * used to expose the AuthenticationManager
     * @return
     * @throws Exception
     */
    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    protected void configure(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                //当有多个 HttpSecurity patterns 只能匹配Order优先级最好的HttpSecurity
                .requestMatchers().antMatchers(login, logout)
                .and().authorizeRequests().anyRequest().authenticated()
                .and()
                .addFilterBefore(beforeLoginFilter(), ChannelProcessingFilter.class)
                .addFilterBefore(new Filter() {
                    @Override
                    public void init(FilterConfig filterConfig) throws ServletException {
                    }

                    @Override
                    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
                        /**
                         * 通用扩展验证，用于其他项目在引用时，自定义validation
                         */
                        if (!authExtValidator.run((HttpServletRequest) request)) {
                            throw new InsufficientAuthenticationException(authExtValidator.getError());
                        }
                        filterChain.doFilter(request, response);
                    }

                    @Override
                    public void destroy() {
                    }
                }, UsernamePasswordAuthenticationFilter.class)
                .formLogin().loginProcessingUrl(login)
                .usernameParameter(usernameParameter)
                .passwordParameter(passwordParameter)
                .successHandler(authenticationSuccessHandler()).failureHandler(httpAuthenticationFailureHandler)
                .and().logout().logoutUrl(logout)
                .logoutSuccessHandler(httpLogoutSuccessHandler).invalidateHttpSession(true)
                .and().csrf().disable();

        //同一个账户多次登录限制，针对等是需要对之前的session进行表示
        httpSecurity
                .sessionManagement()
                .maximumSessions(securityConfig.getMaximumSessions())
//                .maxSessionsPreventsLogin(true)为true是多次登录时抛出异常
                .sessionRegistry(springSessionBackedSessionRegistry());

    }

    @Bean
    public PasswordEncoder passwordEncoder(){
        Pbkdf2PasswordEncoder pbkdf2PasswordEncoder = new Pbkdf2PasswordEncoder("8iekd,a.oa0923.",1850,256);
        return pbkdf2PasswordEncoder;
    }

}
