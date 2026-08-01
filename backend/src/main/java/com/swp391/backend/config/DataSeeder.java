package com.swp391.backend.config;

import com.swp391.backend.entity.*;
import com.swp391.backend.enums.*;
import com.swp391.backend.repository.*;
import com.swp391.backend.service.PromotionReportService;
import com.swp391.backend.service.SlotGenerationService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Configuration
public class DataSeeder {

    @Bean
    @Order(1)
    CommandLineRunner migrateLegacyPasswords(AppUserRepository userRepository, BCryptPasswordEncoder passwordEncoder) {
        return args -> {
            List<AppUser> usersWithPlaintext = userRepository.findAll().stream()
                    .filter(user -> user.getPasswordHash() != null && !user.getPasswordHash().startsWith("$2"))
                    .toList();
            usersWithPlaintext.forEach(user -> user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash())));
            if (!usersWithPlaintext.isEmpty()) {
                userRepository.saveAll(usersWithPlaintext);
            }
        };
    }

    @Bean
    @Order(2)
    CommandLineRunner seedReferenceData(
            RoleRepository roleRepository,
            AppUserRepository userRepository,
            MembershipLevelRepository membershipLevelRepository,
            CustomerMembershipRepository customerMembershipRepository,
            FieldTypeRepository fieldTypeRepository,
            FootballFieldRepository fieldRepository,
            FieldPriceRepository fieldPriceRepository,
            ExtraServiceRepository extraServiceRepository,
            PromotionRepository promotionRepository,
            SystemSettingRepository systemSettingRepository,
            BCryptPasswordEncoder passwordEncoder
    ) {
        return args -> {
            if (roleRepository.count() > 0) {
                return;
            }

            Role customerRole = roleRepository.save(new Role("Customer", "Registered customer who books fields"));
            Role staffRole = roleRepository.save(new Role("Staff", "Venue operation staff"));
            Role adminRole = roleRepository.save(new Role("Admin", "System administrator"));

            AppUser customer = user("Nguyen Van Customer", "customer@goalzone.local", "0900000001", customerRole, passwordEncoder);
            AppUser secondCustomer = user("Le Thi Member", "member@goalzone.local", "0900000002", customerRole, passwordEncoder);
            AppUser staff = user("Staff Operator", "staff@goalzone.local", "0900000003", staffRole, passwordEncoder);
            AppUser admin = user("Admin Manager", "admin@goalzone.local", "0900000004", adminRole, passwordEncoder);
            userRepository.saveAll(List.of(customer, secondCustomer, staff, admin));

            MembershipLevel bronze = membership("Bronze", 0, "0", "Default tier with standard booking rules", 1);
            MembershipLevel silver = membership("Silver", 4, "5", "5% membership discount after 4 completed bookings", 2);
            MembershipLevel gold = membership("Gold", 8, "10", "10% membership discount after 8 completed bookings", 3);
            membershipLevelRepository.saveAll(List.of(bronze, silver, gold));
            customerMembershipRepository.save(customerMembership(customer, bronze, 0));
            customerMembershipRepository.save(customerMembership(secondCustomer, bronze, 0));

            FieldType fiveSide = fieldType("5-a-side", 10, "Fast small-sided matches");
            FieldType sevenSide = fieldType("7-a-side", 14, "Most popular team size");
            fieldTypeRepository.saveAll(List.of(fiveSide, sevenSide));

            FootballField fieldA = field("Field 5A", fiveSide, "Artificial turf field near the entrance", "Zone A - near reception", "Artificial grass", "https://images.unsplash.com/photo-1759210720456-c9814f721479?auto=format&fit=crop&w=1600&q=85");
            FootballField fieldB = field("Field 7A", sevenSide, "Wider field for evening leagues", "Zone B", "Hybrid grass", "https://images.unsplash.com/photo-1712168539418-0aa66404ee0d?auto=format&fit=crop&w=1600&q=85");
            FootballField fieldC = field("Covered Field 5B", fiveSide, "Covered training field for rainy sessions", "Zone B - inner row", "Artificial grass", "https://images.unsplash.com/photo-1690892738385-515d0c045ec0?auto=format&fit=crop&w=1600&q=85");
            fieldRepository.saveAll(List.of(fieldA, fieldB, fieldC));

            fieldPriceRepository.saveAll(List.of(
                    price(fieldA, "weekday", "06:00", "17:00", "11.20"),
                    price(fieldA, "weekday", "17:00", "22:00", "16.80"),
                    price(fieldA, "weekend", "06:00", "22:00", "18.00"),
                    price(fieldB, "weekday", "06:00", "17:00", "16.80"),
                    price(fieldB, "weekday", "17:00", "22:00", "24.80"),
                    price(fieldB, "weekend", "06:00", "22:00", "27.20"),
                    price(fieldC, "all", "06:00", "22:00", "14.40")
            ));

            ExtraService ball = extraService("Ball rental", ServiceType.rental, "ball", "2.00", 30, 2);
            ExtraService bibs = extraService("Bibs set", ServiceType.rental, "set", "2.80", 12, 2);
            ExtraService water = extraService("Water box", ServiceType.sale, "box", "3.60", 50, 5);
            ExtraService referee = extraService("Referee service", ServiceType.staff_service, "match", "10.00", 4, 1);
            extraServiceRepository.saveAll(List.of(ball, bibs, water, referee));

            promotionRepository.save(promotion("WELCOME10", "Welcome 10%", DiscountType.percent, "10", "3.20", "8.00", null, null, "First checkout discount for new customers"));
            Promotion servicePromo = promotion("WATER120", "Water add-on deal", DiscountType.fixed_amount, "1.20", null, "12.00", null, water, "Discount when booking includes water box");
            promotionRepository.save(servicePromo);

            systemSettingRepository.save(setting("deposit.default_percent", "30", "deposit", "Default online booking deposit percent", admin));
            systemSettingRepository.save(setting("payment.pending_timeout_minutes", "15", "payment", "Pending payment timeout before booking expiration", admin));
            systemSettingRepository.save(setting("refund.before_24h_percent", "100", "refund", "Refund percent when cancellation is before 24 hours", admin));
            systemSettingRepository.save(setting("refund.same_day_percent", "80", "refund", "Refund percent for same-day cancellation before check-in", admin));
            systemSettingRepository.save(setting("notification.booking_reminder_hours", "24", "notification", "Hours before a booking to send one reminder", admin));
            systemSettingRepository.save(setting("slot.opening_time", "06:00", "slot_generation", "Daily opening time used to generate bookable slots (HH:mm)", admin));
            systemSettingRepository.save(setting("slot.closing_time", "22:00", "slot_generation", "Daily closing time used to generate bookable slots (HH:mm)", admin));
            systemSettingRepository.save(setting("slot.duration_minutes", "120", "slot_generation", "Length of each automatically generated slot in minutes", admin));
            systemSettingRepository.save(setting("slot.generation_horizon_days", "30", "slot_generation", "Number of days in advance for automatic slot generation", admin));

        };
    }

    @Bean
    @Order(3)
    CommandLineRunner materializeRollingSlotCalendar(SlotGenerationService slotGenerationService) {
        return args -> slotGenerationService.generateRollingWindow();
    }

    @Bean
    @Order(4)
    CommandLineRunner reconcileMembershipProgress(PromotionReportService promotionReportService) {
        return args -> promotionReportService.reconcileMembershipAssignments();
    }

    private AppUser user(String fullName, String email, String phone, Role role, BCryptPasswordEncoder passwordEncoder) {
        AppUser user = new AppUser();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode("GoalZone@123"));
        user.setRole(role);
        user.setEmailVerified(true);
        return user;
    }

    private MembershipLevel membership(String name, int required, String discount, String benefit, int order) {
        MembershipLevel level = new MembershipLevel();
        level.setLevelName(name);
        level.setRequiredCompletedBookings(required);
        level.setDiscountPercent(new BigDecimal(discount));
        level.setBenefitDescription(benefit);
        level.setDisplayOrder(order);
        return level;
    }

    private CustomerMembership customerMembership(AppUser customer, MembershipLevel level, int completedCount) {
        CustomerMembership membership = new CustomerMembership();
        membership.setCustomer(customer);
        membership.setMembershipLevel(level);
        membership.setCompletedBookingCount(completedCount);
        membership.setEffectiveFrom(LocalDate.now().minusMonths(1));
        membership.setProgressNote("Membership progress is based on completed bookings");
        return membership;
    }

    private FieldType fieldType(String name, int capacity, String description) {
        FieldType type = new FieldType();
        type.setTypeName(name);
        type.setPlayerCapacity(capacity);
        type.setDescription(description);
        return type;
    }

    private FootballField field(String name, FieldType type, String description, String location, String surface, String imageUrl) {
        FootballField field = new FootballField();
        field.setFieldName(name);
        field.setFieldType(type);
        field.setDescription(description);
        field.setLocation(location);
        field.setSurfaceType(surface);
        field.setImageUrl(imageUrl);
        return field;
    }

    private FieldPrice price(FootballField field, String dayType, String start, String end, String amount) {
        FieldPrice price = new FieldPrice();
        price.setField(field);
        price.setDayType(dayType);
        price.setStartTime(LocalTime.parse(start));
        price.setEndTime(LocalTime.parse(end));
        price.setPrice(new BigDecimal(amount));
        price.setEffectiveFrom(LocalDate.now().minusMonths(1));
        return price;
    }

    private ExtraService extraService(String name, ServiceType type, String unit, String price, int stock, int maxPerBooking) {
        ExtraService service = new ExtraService();
        service.setServiceName(name);
        service.setServiceType(type);
        service.setUnitName(unit);
        service.setUnitPrice(new BigDecimal(price));
        service.setStockQuantity(stock);
        service.setMaxQuantityPerBooking(maxPerBooking);
        service.setDescription(name + " available as a booking add-on");
        return service;
    }

    private Promotion promotion(String code, String name, DiscountType type, String value, String max, String min, FieldType fieldType, ExtraService service, String description) {
        Promotion promotion = new Promotion();
        promotion.setPromotionCode(code);
        promotion.setPromotionName(name);
        promotion.setDiscountType(type);
        promotion.setDiscountValue(new BigDecimal(value));
        promotion.setMaxDiscountAmount(max == null ? null : new BigDecimal(max));
        promotion.setMinBookingAmount(min == null ? null : new BigDecimal(min));
        promotion.setStartDate(LocalDate.now().minusDays(3));
        promotion.setEndDate(LocalDate.now().plusMonths(1));
        promotion.setUsageLimit(100);
        promotion.setApplicableFieldType(fieldType);
        promotion.setApplicableExtraService(service);
        promotion.setDescription(description);
        return promotion;
    }

    private SystemSetting setting(String key, String value, String group, String description, AppUser admin) {
        SystemSetting setting = new SystemSetting();
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        setting.setSettingGroup(group);
        setting.setDescription(description);
        setting.setUpdatedBy(admin);
        return setting;
    }
}
