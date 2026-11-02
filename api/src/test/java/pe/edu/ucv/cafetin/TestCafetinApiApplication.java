package pe.edu.ucv.cafetin;

import org.springframework.boot.SpringApplication;

public class TestCafetinApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(CafetinApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
