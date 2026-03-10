package com.example.literalura.service;

import com.example.literalura.model.Autor;
import com.example.literalura.model.Libro;
import com.example.literalura.repository.AutorRepository;
import com.example.literalura.repository.LibroRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

@Service
public class Principal {

    @Autowired
    private LibroRepository libroRepository;

    @Autowired
    private AutorRepository autorRepository;

    private Scanner scanner = new Scanner(System.in);
    private ObjectMapper mapper = new ObjectMapper();

    public void mostrarMenu() {
        int opcion = -1;

        while (opcion != 0) {
            System.out.println("\n---- LITERALURA ----");
            System.out.println("1 - Buscar libro por titulo");
            System.out.println("2 - Listar libros registrados");
            System.out.println("3 - Listar autores registrados");
            System.out.println("4 - Listar autores vivos en un determinado año");
            System.out.println("5 - Listar libros por idioma");
            System.out.println("0 - Salir");
            System.out.print("Elige una opcion: ");

            opcion = scanner.nextInt();
            scanner.nextLine();

            switch (opcion) {
                case 1:
                    buscarLibro();
                    break;
                case 2:
                    listarLibros();
                    break;
                case 3:
                    listarAutores();
                    break;
                case 4:
                    listarAutoresVivos();
                    break;
                case 5:
                    listarLibrosPorIdioma();
                    break;
                case 0:
                    System.out.println("Cerrando la aplicacion...");
                    break;
                default:
                    System.out.println("Opcion invalida");
            }
        }
    }

    private void buscarLibro() {
        System.out.print("Escribe el nombre del libro que deseas buscar: ");
        String nombreLibro = scanner.nextLine();

        // verificar si ya esta en la base de datos
        Optional<Libro> libroExistente = libroRepository.findByTitulo(nombreLibro);
        if (libroExistente.isPresent()) {
            System.out.println("El libro ya esta registrado en la base de datos");
            System.out.println(libroExistente.get());
            return;
        }

        // consultar la API
        try {
            String url = "https://gutendex.com/books/?search=" + nombreLibro.replace(" ", "%20");

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String json = response.body();

            JsonNode root = mapper.readTree(json);
            JsonNode resultados = root.get("results");

            if (resultados == null || resultados.isEmpty()) {
                System.out.println("No se encontro el libro");
                return;
            }

            JsonNode primerLibro = resultados.get(0);

            String titulo = primerLibro.get("title").asText();
            Integer descargas = primerLibro.get("download_count").asInt();

            // idioma
            String idioma = "desconocido";
            if (primerLibro.get("languages") != null && primerLibro.get("languages").size() > 0) {
                idioma = primerLibro.get("languages").get(0).asText();
            }

            // autor - solo tomamos el primero
            Autor autor = null;
            JsonNode autores = primerLibro.get("authors");
            if (autores != null && autores.size() > 0) {
                JsonNode primerAutor = autores.get(0);
                String nombreAutor = primerAutor.get("name").asText();

                // ver si el autor ya existe
                Optional<Autor> autorExistente = autorRepository.findByNombre(nombreAutor);
                if (autorExistente.isPresent()) {
                    autor = autorExistente.get();
                } else {
                    autor = new Autor();
                    autor.setNombre(nombreAutor);

                    if (!primerAutor.get("birth_year").isNull()) {
                        autor.setAnioNacimiento(primerAutor.get("birth_year").asInt());
                    }
                    if (!primerAutor.get("death_year").isNull()) {
                        autor.setAnioFallecimiento(primerAutor.get("death_year").asInt());
                    }

                    autorRepository.save(autor);
                }
            }

            Libro libro = new Libro();
            libro.setTitulo(titulo);
            libro.setIdioma(idioma);
            libro.setNumeroDescargas(descargas);
            libro.setAutor(autor);

            libroRepository.save(libro);

            System.out.println("\nLibro guardado:");
            System.out.println(libro);

        } catch (Exception e) {
            System.out.println("Error al buscar el libro: " + e.getMessage());
        }
    }

    private void listarLibros() {
        List<Libro> libros = libroRepository.findAll();

        if (libros.isEmpty()) {
            System.out.println("No hay libros registrados");
        } else {
            System.out.println("\n--- Libros registrados ---");
            for (Libro libro : libros) {
                System.out.println(libro);
            }
        }
    }

    private void listarAutores() {
        List<Autor> autores = autorRepository.findAll();

        if (autores.isEmpty()) {
            System.out.println("No hay autores registrados");
        } else {
            System.out.println("\n--- Autores registrados ---");
            for (Autor autor : autores) {
                System.out.println(autor);
            }
        }
    }

    private void listarAutoresVivos() {
        System.out.print("Ingresa el año: ");
        int anio = scanner.nextInt();
        scanner.nextLine();

        List<Autor> autores = autorRepository.buscarAutoresVivosEnAnio(anio);

        if (autores.isEmpty()) {
            System.out.println("No se encontraron autores vivos en el año " + anio);
        } else {
            System.out.println("\n--- Autores vivos en " + anio + " ---");
            for (Autor autor : autores) {
                System.out.println(autor);
            }
        }
    }

    private void listarLibrosPorIdioma() {
        System.out.println("Ingresa el idioma:");
        System.out.println("es - español");
        System.out.println("en - ingles");
        System.out.println("fr - frances");
        System.out.println("pt - portugues");
        System.out.print("Opcion: ");
        String idioma = scanner.nextLine();

        List<Libro> libros = libroRepository.findByIdioma(idioma);

        if (libros.isEmpty()) {
            System.out.println("No hay libros en ese idioma");
        } else {
            System.out.println("\n--- Libros en " + idioma + " ---");
            for (Libro libro : libros) {
                System.out.println(libro);
            }
        }
    }
}
