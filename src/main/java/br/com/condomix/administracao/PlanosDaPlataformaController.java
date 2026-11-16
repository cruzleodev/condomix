package br.com.condomix.administracao;

import br.com.condomix.plataforma.CatalogoDePlanos;
import br.com.condomix.plataforma.CatalogoDePlanos.PlanoNoCatalogo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "Plataforma: planos")
@RestController
@RequestMapping("/api/plataforma/planos")
class PlanosDaPlataformaController {

	private final CatalogoDePlanos catalogo;

	PlanosDaPlataformaController(CatalogoDePlanos catalogo) {
		this.catalogo = catalogo;
	}

	record PedidoPrecos(@DecimalMin("0") BigDecimal mensal, @DecimalMin("0") BigDecimal semestral,
			@DecimalMin("0") BigDecimal anual) {
	}

	@Operation(summary = "Planos, módulos e preços")
	@GetMapping
	List<PlanoNoCatalogo> listar() {
		return catalogo.listar();
	}

	@Operation(summary = "Definir os preços de um plano (em reais; deixe vazio a periodicidade não vendida)")
	@PutMapping("/{codigo}/precos")
	PlanoNoCatalogo definirPrecos(@PathVariable String codigo, @Valid @RequestBody PedidoPrecos pedido) {
		return catalogo.definirPrecos(codigo, pedido.mensal(), pedido.semestral(), pedido.anual());
	}

}
