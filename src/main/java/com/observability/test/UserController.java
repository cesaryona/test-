package com.observability.test;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class UserController {

    @PostMapping("/users")
    public void u(@RequestBody User user) {
        log.info("teste post user - {}", user);
    }

    @GetMapping("/users")
    public User createUser(@RequestBody User user) {
        User user1 = new User("João Silva", 30, "123.456.789-10");
        log.info("Criando usuário: {}", user1);

        User user2 = new User("Maria Santos", 25, "987.654.321-00");
        log.info("Criando usuário: {}", user2);

        User user3 = new User("Pedro Oliveira", 40, "111.222.333-44");
        log.info("Criando usuário: {}", user3);

        // ==================== CPF SEM FORMATO ====================
        User user4 = new User("Ana Costa", 28, "12345678901");
        log.info("Criando usuário: {}", user4);

        User user5 = new User("Carlos Souza", 35, "98765432100");
        log.info("Criando usuário: {}", user5);

        User user6 = new User("Juliana Lima", 22, "11122233344");
        log.info("Criando usuário: {}", user6);

        // ==================== CNPJ FORMATADO ====================
        User company1 = new User("Tech Solutions LTDA", null, "12.345.678/0001-99");
        log.info("Criando empresa: {}", company1);

        User company2 = new User("Inovação Digital SA", null, "98.765.432/0001-10");
        log.info("Criando empresa: {}", company2);

        User company3 = new User("StartUp Brasil ME", null, "11.222.333/0001-44");
        log.info("Criando empresa: {}", company3);

        // ==================== CNPJ SEM FORMATO ====================
        User company4 = new User("Mega Corp", null, "12345678000199");
        log.info("Criando empresa: {}", company4);

        User company5 = new User("Super Business", null, "98765432000110");
        log.info("Criando empresa: {}", company5);

        User company6 = new User("FastGrow Inc", null, "11222333000144");
        log.info("Criando empresa: {}", company6);

        // ==================== EDGE CASES ====================
        User user7 = new User("José Santos", 45, "555.666.777-88");
        User user8 = new User("Fernanda Alves", 33, "55566677788");
        log.info("Transação: {} -> {}", user7, user8);

        User user9 = new User("Roberto Silva", 50, "999.888.777-66");
        log.info("Pedido aprovado para: {}", user9);

        User user10 = new User("Empresa XYZ", null, "33.444.555/0001-22");
        log.info("Pagamento processado: {}", user10);

        log.info("========== TESTES FINALIZADOS ==========");

        return null;
    }
}