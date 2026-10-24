package com.pgds.card;

import com.pgds.card.CardDtos.*;
import com.pgds.common.ApiException;
import com.pgds.domain.*;
import com.pgds.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
public class BeneficiaryService {

    private static final int MINIMUM_NAME_SIMILARITY_PERCENT = 85;

    private final BeneficiaryRepository beneficiaries;
    private final RationCardRepository cards;
    private final UserRepository users;

    public BeneficiaryView create(BeneficiaryRequest r) {
        RationCard card = cards.findById(r.rationCardId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ration card not found"));
        if (!card.isEligible())
            throw new ApiException(HttpStatus.CONFLICT, "Ration card is not eligible");
        if (beneficiaries.countByRationCardId(card.getId()) >= card.getFamilyMembers())
            throw new ApiException(HttpStatus.CONFLICT, "Card already has " + card.getFamilyMembers() + " registered members");
        // Duplicate guard (basic): same name + same Aadhaar last-4 anywhere in the system.
        // Fuzzy matching across spellings is planned for the AI sprint.
        if (beneficiaries.existsByAadhaarLast4AndNameIgnoreCase(r.aadhaarLast4(), r.name().trim()))
            throw new ApiException(HttpStatus.CONFLICT, "Possible duplicate beneficiary already registered");

        AppUser linked = null;
        if (r.linkUsername() != null && !r.linkUsername().isBlank()) {
            linked = users.findByUsername(r.linkUsername())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Linked user not found"));
            if (linked.getRole() != Role.BENEFICIARY)
                throw new ApiException(HttpStatus.BAD_REQUEST, "Linked user is not a BENEFICIARY account");
            if (beneficiaries.existsByUserId(linked.getId()))
                throw new ApiException(HttpStatus.CONFLICT, "That user is already linked to a beneficiary");
        }
        return toView(beneficiaries.save(Beneficiary.builder().name(r.name().trim()).age(r.age())
                .aadhaarLast4(r.aadhaarLast4()).rationCard(card).user(linked).build()));
    }

    @Transactional(readOnly = true)
    public List<BeneficiaryView> byCard(Long cardId) {
        return beneficiaries.findByRationCardId(cardId).stream().map(BeneficiaryService::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<DuplicateBeneficiaryMatch> possibleDuplicates() {
        List<Beneficiary> candidates = beneficiaries.findPossibleDuplicateCandidates();
        List<DuplicateBeneficiaryMatch> matches = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            Beneficiary first = candidates.get(i);
            for (int j = i + 1; j < candidates.size(); j++) {
                Beneficiary second = candidates.get(j);
                if (!first.getAadhaarLast4().equals(second.getAadhaarLast4())) continue;
                int similarity = nameSimilarityPercent(first.getName(), second.getName());
                if (similarity < MINIMUM_NAME_SIMILARITY_PERCENT) continue;
                matches.add(new DuplicateBeneficiaryMatch(first.getId(), first.getName(),
                        first.getRationCard().getCardNumber(), second.getId(), second.getName(),
                        second.getRationCard().getCardNumber(), "XXXX-XXXX-" + first.getAadhaarLast4(), similarity));
            }
        }
        return matches.stream()
                .sorted(Comparator.comparingInt(DuplicateBeneficiaryMatch::nameSimilarityPercent).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public BeneficiaryView get(Long id) {
        return toView(beneficiaries.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Beneficiary not found")));
    }

    public void delete(Long id) {
        if (!beneficiaries.existsById(id)) throw new ApiException(HttpStatus.NOT_FOUND, "Beneficiary not found");
        beneficiaries.deleteById(id);
    }

    public static BeneficiaryView toView(Beneficiary b) {
        return new BeneficiaryView(b.getId(), b.getName(), b.getAge(), "XXXX-XXXX-" + b.getAadhaarLast4(),
                b.getRationCard().getId(), b.getRationCard().getCardNumber());
    }

    static int nameSimilarityPercent(String first, String second) {
        String left = normalizeName(first);
        String right = normalizeName(second);
        int longest = Math.max(left.length(), right.length());
        if (longest == 0) return 100;
        return (int) Math.round(100.0 * (longest - editDistance(left, right)) / longest);
    }

    private static String normalizeName(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static int editDistance(String first, String second) {
        int[] previous = new int[second.length() + 1];
        for (int j = 0; j <= second.length(); j++) previous[j] = j;
        for (int i = 1; i <= first.length(); i++) {
            int[] current = new int[second.length() + 1];
            current[0] = i;
            for (int j = 1; j <= second.length(); j++) {
                int substitutionCost = first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + substitutionCost);
            }
            previous = current;
        }
        return previous[second.length()];
    }
}
