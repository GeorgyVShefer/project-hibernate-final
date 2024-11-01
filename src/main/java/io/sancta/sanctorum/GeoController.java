package io.sancta.sanctorum;

import io.lettuce.core.RedisClient;
import io.sancta.sanctorum.dao.CityDAO;
import io.sancta.sanctorum.dao.CountryDAO;
import io.sancta.sanctorum.domain.City;
import io.sancta.sanctorum.domain.Country;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GeoController {
    SessionFactory sessionFactory;
    CityDAO cityDAO;
    CountryDAO countryDAO;
    RedisClient redisClient;

    public GeoController() {
        sessionFactory = setupRelationalDatabase();
        cityDAO = new CityDAO(sessionFactory);
        countryDAO = new CountryDAO(sessionFactory);
        redisClient = setupRedisClient();
    }

    public void run(){
        List<City> cities = fetchData();

        shutdown();
    }

    private SessionFactory setupRelationalDatabase(){
        return new Configuration().configure().buildSessionFactory();
    }

    private RedisClient setupRedisClient(){
        return RedisClient.create();
    }

    private void shutdown(){
        if(Objects.nonNull(sessionFactory)) sessionFactory.close();

        if(Objects.nonNull(redisClient)) redisClient.shutdown();
    }

    private List<City> fetchData(){
        try (Session session = sessionFactory.getCurrentSession()) {
           List<City> allCities = new ArrayList<>();
            session.beginTransaction();
            List<Country> countries = countryDAO.getAll();

            int totalCount = cityDAO.getTotalCount();
            int step = 500;

            for (int i = 0; i < totalCount; i+=step) {
                allCities.addAll(cityDAO.getItems(i, step));
            }

            session.getTransaction().commit();
            return allCities;
        }
    }
}
