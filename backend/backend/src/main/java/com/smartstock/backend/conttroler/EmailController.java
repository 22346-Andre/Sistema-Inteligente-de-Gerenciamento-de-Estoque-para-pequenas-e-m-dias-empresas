package com.smartstock.backend.conttroler;

import com.smartstock.backend.service.EmailService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/email")
public class EmailController {

    private final EmailService emailService;


    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostMapping("/send")
    public String send(){
        emailService.sendEmail(
                "andrelucasreis2004t@gmail.com",
                "Email de Teste",
                " Pronto, deu certo "
        );

        return "email sent succesfully";
    }


}