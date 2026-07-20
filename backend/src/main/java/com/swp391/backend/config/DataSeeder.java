package com.swp391.backend.config;

import com.swp391.backend.entity.*;
import com.swp391.backend.enums.*;
import com.swp391.backend.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class DataSeeder {

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @PostConstruct
    public void migrateExistingPasswords() {
        List<AppUser> usersWithPlaintext = userRepository.findAll()
                .stream()
                .filter(user -> user.getPasswordHash() != null && !user.getPasswordHash().startsWith("$2"))
                .collect(Collectors.toList());

        if (usersWithPlaintext.isEmpty()) {
            return;
        }

        usersWithPlaintext.forEach(user -> user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash())));
        userRepository.saveAll(usersWithPlaintext);
    }

    @Bean
    CommandLineRunner seedDemoData(
            RoleRepository roleRepository,
            AppUserRepository userRepository,
            MembershipLevelRepository membershipLevelRepository,
            CustomerMembershipRepository customerMembershipRepository,
            FieldTypeRepository fieldTypeRepository,
            FootballFieldRepository fieldRepository,
            FieldPriceRepository fieldPriceRepository,
            SlotRepository slotRepository,
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
            customerMembershipRepository.save(customerMembership(customer, bronze, 1));
            customerMembershipRepository.save(customerMembership(secondCustomer, silver, 5));

            FieldType fiveSide = fieldType("5-a-side", 10, "Fast small-sided matches");
            FieldType sevenSide = fieldType("7-a-side", 14, "Most popular team size");
            fieldTypeRepository.saveAll(List.of(fiveSide, sevenSide));

            FootballField fieldA = field("Pitch A", fiveSide, "Artificial turf pitch near the entrance", "Zone A", "Artificial grass", "https://images.unsplash.com/photo-1522778119026-d647f0596c20?auto=format&fit=crop&w=1200&q=80");
            FootballField fieldB = field("Pitch B", sevenSide, "Wider pitch for evening leagues", "Zone B", "Hybrid grass", "https://images.unsplash.com/photo-1575361204480-aadea25e6e68?auto=format&fit=crop&w=1200&q=80");
            FootballField fieldC = field("Pitch C", fiveSide, "Covered training pitch for rainy sessions", "Zone C", "Artificial grass", "https://images.unsplash.com/photo-1556056504-5c7696c4c28d?auto=format&fit=crop&w=1200&q=80");
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

            seedSlots(slotRepository, fieldA, fieldB, fieldC, staff);

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

        };
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
        membership.setProgressNote("Seeded demo membership");
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

    private void seedSlots(SlotRepository slotRepository, FootballField fieldA, FootballField fieldB, FootballField fieldC, AppUser staff) {
        List<FootballField> fields = List.of(fieldA, fieldB, fieldC);
        List<String> starts = List.of("06:00", "08:00", "17:00", "19:00");
        for (int day = 1; day <= 5; day++) {
            LocalDate date = LocalDate.now().plusDays(day);
            for (FootballField field : fields) {
                for (String start : starts) {
                    LocalTime startTime = LocalTime.parse(start);
                    Slot slot = new Slot();
                    slot.setField(field);
                    slot.setSlotDate(date);
                    slot.setStartTime(startTime);
                    slot.setEndTime(startTime.plusHours(2));
                    slot.setCreatedBy(staff);
                    if (day == 2 && field == fieldB && start.equals("17:00")) {
                        slot.setStatus(SlotStatus.blocked);
                        slot.setBlockReason("maintenance");
                        slot.setBlockNote("Lighting maintenance");
                    }
                    slotRepository.save(slot);
                }
            }
        }
    }

    private ExtraService extraService(String name, ServiceType type, String unit, String price, int stock, int maxPerBooking) {
        ExtraService service = new ExtraService();
        service.setServiceName(name);
        service.setServiceType(type);
        service.setUnitName(unit);
        service.setUnitPrice(new BigDecimal(price));
        service.setStockQuantity(stock);
        service.setMaxQuantityPerBooking(maxPerBooking);
        service.setDescription("Seeded " + name.toLowerCase());
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
