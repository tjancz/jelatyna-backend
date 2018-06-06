package pl.confitura.jelatyna.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import pl.confitura.jelatyna.infrastructure.security.SecurityHelper;
import pl.confitura.jelatyna.registration.ParticipationData;
import pl.confitura.jelatyna.registration.ParticipationRepository;
import pl.confitura.jelatyna.registration.voucher.Voucher;
import pl.confitura.jelatyna.registration.voucher.VoucherService;

import javax.persistence.EntityManager;
import java.util.List;

import static java.time.LocalDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test, fake-db")
@Transactional
public class UserRepositoryTest {
    @Autowired
    private EntityManager em;

    @Autowired
    private UserRepository repository;

    @Test
    void should_save_a_user() {
        User user = new User()
                .setOrigin("twitter")
                .setSocialId("1234");

        User saved = repository.save(user);

        assertThat(repository.findById(saved.getId()))
                .isEqualToComparingOnlyGivenFields(user, "origin", "socialId");
    }

    @Test
    void should_find_a_user_by_social_id() {
        User user = repository.save(new User()
                .setOrigin("twitter")
                .setSocialId("1"));
        repository.save(new User()
                .setOrigin("twitter")
                .setSocialId("2"));

        User found = repository.findBySocialId("1");

        assertThat(found).isEqualTo(user);
    }

    @Test
    void should_check_if_exists_by_social_id() {
        repository.save(new User()
                .setOrigin("twitter")
                .setSocialId("1"));

        assertThat(repository
                .existsBySocialId("1")).isTrue();
        assertThat(repository
                .existsBySocialId("2")).isFalse();
    }

    @Autowired
    VoucherService voucherService;

    @Autowired
    ParticipationRepository participationRepository;

    @BeforeEach
    void setup() {
        SecurityHelper.asAdmin();
    }

    @Test
    void should_send_ticket_to_user_that_is_registered_and_have_not_received_ticket_yet() {
        // given user registered as participationData with proper voucher
        Voucher voucher = voucherService.generateVoucher("");
        ParticipationData participationData = participationRepository.save(new ParticipationData().setVoucher(voucher));
        User user = repository.save(new User().setParticipationData(participationData));

        // when admin gets users to send ticket to
        List<User> list = repository.findUsersToSendTickets();

        //then user is in list

        assertThat(list).contains(user);
    }

    @Test
    void should_not_send_ticket_to_user_that_have_registered_when_ticket_was_already_sent() {

        // given user registered as participationData with proper voucher
        Voucher voucher = voucherService.generateVoucher("");
        //and user have received ticket
        ParticipationData participationData = participationRepository.save(new ParticipationData().setVoucher(voucher).setTicketSendDate(now()));
        User user = repository.save(new User().setParticipationData(participationData));

        // when admin gets users to send ticket to
        List<User> list = repository.findUsersToSendTickets();

        //then user is not in list
        assertThat(list).doesNotContain(user);

    }

    @Test
    void should_not_send_ticket_to_user_that_have_not_registered_as_participant() {

        // given user not registered as participationData
        User user = repository.save(new User());

        // when admin gets users to send ticket to
        List<User> list = repository.findUsersToSendTickets();

        //then user is not in list
        assertThat(list).doesNotContain(user);

    }

    @Test
    void should_not_send_ticket_to_user_that_have_registered_as_participant_and_does_not_own_voucher() {
        // given user registered as participationData without proper voucher
        ParticipationData participant = participationRepository.save(new ParticipationData());
        User user = repository.save(new User().setParticipationData(participant));

        // when admin gets users to send ticket to
        List<User> list = repository.findUsersToSendTickets();

        //then user is not in list
        assertThat(list).doesNotContain(user);

    }


    @Test
    void admin_should_get_users_that_arrived_to_conference(){
        ParticipationData registered = participationRepository.save(new ParticipationData().setArrivalDate(now()));
        User registeredUser = repository.save(new User().setParticipationData(registered));
        User notRegisteredUser = repository.save(new User());

        //when
        List<User> allRegistered = repository.findAllPresentOnConference();

        //then
        assertThat(allRegistered)
                .contains(registeredUser)
                .doesNotContain(notRegisteredUser);
    }
}