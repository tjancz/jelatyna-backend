package pl.confitura.jelatyna.user;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.util.StringUtils.isEmpty;

import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.RepositoryRestController;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.Resources;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.timgroup.jgravatar.Gravatar;
import com.timgroup.jgravatar.GravatarDefaultImage;
import com.timgroup.jgravatar.GravatarRating;
import pl.confitura.jelatyna.infrastructure.security.Security;
import pl.confitura.jelatyna.presentation.Presentation;
import pl.confitura.jelatyna.presentation.PresentationRepository;

@RepositoryRestController
public class UserController {

    private UserRepository repository;
    private PresentationRepository presentationRepository;
    private Security security;

    @Autowired
    public UserController(UserRepository repository, PresentationRepository presentationRepository, Security security) {
        this.repository = repository;
        this.presentationRepository = presentationRepository;
        this.security = security;
    }

    @PostMapping("/users")
    @PreAuthorize("@security.isOwner(#user.id)")
    public ResponseEntity<?> save(@RequestBody User user) {
        if (isEmpty(user.getPhoto())) {
            Gravatar gravatar = new Gravatar(300, GravatarRating.GENERAL_AUDIENCES, GravatarDefaultImage.BLANK);
            String url = gravatar.getUrl(user.getEmail());
            user.setPhoto(url);
        }
        User saved = repository.save(user);
        return ResponseEntity.ok(new Resource<>(saved));
    }

    @PostMapping("/users/{userId}/volunteer/{isVolunteer}")
    @PreAuthorize("@security.isAdmin()")
    @Transactional
    public ResponseEntity<Object> markAsVolunteer(@PathVariable("userId") String userId,
            @PathVariable("isVolunteer") boolean isVolunteer) {
        User user = repository.findOne(userId);
        user.setVolunteer(isVolunteer);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/users/{userId}/presentations")
    @PreAuthorize("@security.isOwner(#userId)")
    public ResponseEntity<?> addPresentationToUser(@Valid @RequestBody Presentation presentation,
            @PathVariable String userId) {
        if (isEmpty(presentation.getId()) && !security.isAdmin()) {
            return ResponseEntity.status(UNAUTHORIZED).build();
        }
        User speaker = repository.findOne(userId);
        presentation.setSpeaker(speaker);
        retainStatus(presentation);
        Presentation saved = presentationRepository.save(presentation);
        return ResponseEntity.ok(new Resource<>(saved));
    }

    @GetMapping("/users/search/speakers")
    public ResponseEntity<?> getSpeakers() {
        Set<Resource<?>> speakers = Streams.stream(repository.findAllAccepted())
                .flatMap(row -> {
                    Set<User> users = Sets.newHashSet((User) row[0]);
                    if (row[1] != null) {
                        users.add((User) row[1]);
                    }
                    return users.stream();
                })
                .distinct()
                .map(user -> new Resource<>(user))
                .collect(Collectors.toSet());
        return ResponseEntity.ok(new Resources<>(speakers));
    }

    private void retainStatus(Presentation presentation) {
        if (!isEmpty(presentation.getId())) {
            Presentation saved = presentationRepository.findOne(presentation.getId());
            presentation.setStatus(saved.getStatus());
        }
    }
}
