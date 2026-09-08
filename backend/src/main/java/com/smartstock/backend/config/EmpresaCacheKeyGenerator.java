package com.smartstock.backend.config;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Gera a chave do cache a partir do método + empresaId do usuário logado
 * (claim "empresaId" do JWT), sem precisar mudar a assinatura dos métodos
 * de serviço que hoje pegam a empresa via SecurityContextHolder.
 *
 * IMPORTANTE (isolamento de tenant): é isso aqui que garante que o cache do
 * dashboard da empresa A nunca seja servido pra empresa B. Se algum método
 * novo for anotado com @Cacheable(keyGenerator = "empresaCacheKeyGenerator")
 * fora de um contexto autenticado, ele vai estourar exceção em vez de
 * cachear errado — comportamento proposital.
 */
@Component("empresaCacheKeyGenerator")
public class EmpresaCacheKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long empresaId = jwt.getClaim("empresaId");
        return method.getName() + "_empresa_" + empresaId;
    }
}
