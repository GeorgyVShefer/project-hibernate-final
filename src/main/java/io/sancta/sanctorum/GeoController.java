package io.sancta.sanctorum;

import com.google.gson.Gson;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.sancta.sanctorum.dao.CityDAO;
import io.sancta.sanctorum.dao.CountryDAO;
import io.sancta.sanctorum.domain.City;
import io.sancta.sanctorum.domain.Country;
import io.sancta.sanctorum.domain.CountryLanguage;
import io.sancta.sanctorum.redis.CityCountry;
import io.sancta.sanctorum.redis.Language;
import lombok.AccessLevel;

import lombok.experimental.FieldDefaults;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import org.hibernate.cfg.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GeoController {
    SessionFactory sessionFactory;
    CityDAO cityDAO;
    CountryDAO countryDAO;
    RedisClient redisClient;
    Gson gson;

    public GeoController() {
        sessionFactory = setupRelationalDatabase();
        cityDAO = new CityDAO(sessionFactory);
        countryDAO = new CountryDAO(sessionFactory);
        redisClient = setupRedisClient();
        gson = new Gson().newBuilder()
                .setPrettyPrinting().create();
    }

    public void run() {
        List<City> cities = fetchData();
        List<CityCountry> prepareData = transformData(cities);
        pushToRedis(prepareData);

        sessionFactory.getCurrentSession().close();

        benchmarkDatabasePerformance();
        shutdown();
    }

    private void benchmarkDatabasePerformance() {
        List<Integer> ids = List.of(4002, 309, 913, 123, 213, 3344, 2241, 219, 712, 12, 2211, 1934);

        long startRedis = System.currentTimeMillis();
        testRedisData(ids);
        long stopRedis = System.currentTimeMillis();

        long startMySQL = System.currentTimeMillis();
        testMySqlData(ids);
        long stopMySQL = System.currentTimeMillis();


        System.out.printf("%s: %d ms \n", "Redis", stopRedis - startRedis);
        System.out.printf("%s: %d ms\n", "MySQL", stopMySQL - startMySQL);
    }

    private SessionFactory setupRelationalDatabase() {
        return new Configuration().configure().buildSessionFactory();
    }

    private RedisClient setupRedisClient() {
        RedisClient redisClient = RedisClient.create(RedisURI.create("localhost", 6379));

        try (StatefulRedisConnection<String, String> connect = redisClient.connect()) {
            System.out.println("\nConnected to redis.....\n");
        }
        return redisClient;
    }

    private void shutdown() {
        if (Objects.nonNull(sessionFactory)) sessionFactory.close();

        if (Objects.nonNull(redisClient)) redisClient.shutdown();
    }

    private List<City> fetchData() {
        try (Session session = sessionFactory.getCurrentSession()) {
            List<City> allCities = new ArrayList<>();
            session.beginTransaction();
            List<Country> countries = countryDAO.getAll();

            int totalCount = cityDAO.getTotalCount();
            int step = 500;

            for (int i = 0; i < totalCount; i += step) {
                allCities.addAll(cityDAO.getItems(i, step));
            }

            session.getTransaction().commit();
            return allCities;
        }
    }

    private List<CityCountry> transformData(List<City> cities) {
        return cities.stream()
                .map(city ->
                        CityCountry.builder()
                                .id(city.getId())
                                .name(city.getName())
                                .district(city.getDistrict())
                                .population(city.getPopulation())
                                .countryCode(city.getCountry().getCode())
                                .alternativeCountryCode(city.getCountry().getAlternativeCode())
                                .countryName(city.getCountry().getName())
                                .continent(city.getCountry().getContinent())
                                .countryRegion(city.getCountry().getRegion())
                                .countrySurfaceArea(city.getCountry().getSurfaceArea())
                                .countryPopulation(city.getCountry().getPopulation())
                                .languages(city.getCountry().getLanguages().stream()
                                        .map(countryLanguage ->
                                                Language.builder()
                                                        .language(countryLanguage.getLanguage())
                                                        .isOfficial(countryLanguage.getIsOfficial())
                                                        .percentage(countryLanguage.getPercentage())
                                                        .build()
                                        ).collect(Collectors.toSet())
                                ).build()
                ).toList();
    }

    private void pushToRedis(List<CityCountry> data) {
        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            RedisCommands<String, String> sync = connection.sync();
            for (CityCountry cityCountry : data) {
                sync.set(String.valueOf(cityCountry.getId()), gson.toJson(cityCountry));
            }
        }
    }

    private void testRedisData(List<Integer> ids) {
        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            RedisCommands<String, String> sync = connection.sync();
            for (Integer id : ids) {
                String value = sync.get(String.valueOf(id));
                gson.fromJson(value, CityCountry.class);
            }
        }
    }

    private void testMySqlData(List<Integer> ids) {
        try (Session session = sessionFactory.getCurrentSession()) {
            session.beginTransaction();
            for (Integer id : ids) {
                City city = cityDAO.getById(id);
                Set<CountryLanguage> languages = city.getCountry().getLanguages();
            }
            session.getTransaction().commit();
        }
    }
}
