package com.smartdoor.backend.credentials.repository;

import com.smartdoor.backend.credentials.entity.RfidCredential;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Chỉ service của module sở hữu sử dụng; truy vấn không thay thế authorization. */
public interface RfidCredentialRepository extends JpaRepository<RfidCredential, Long> {

	Optional<RfidCredential> findByDoorIdAndUidLookupValue(Long doorId, String uidLookupValue);

	Optional<RfidCredential> findByIdAndDoorIdAndAccountId(Long id, Long doorId, Long accountId);

	Page<RfidCredential> findAllByDoorIdAndAccountId(Long doorId, Long accountId, Pageable pageable);

	List<RfidCredential> findAllByCardholderIdAndAccountId(Long cardholderId, Long accountId);

	List<RfidCredential> findAllByDoorId(Long doorId);

}
