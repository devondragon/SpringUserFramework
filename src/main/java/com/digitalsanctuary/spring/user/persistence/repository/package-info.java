/**
 * This package contains the repository interfaces for the Spring User Framework.
 *
 * <p>
 * The repository interfaces are responsible for providing CRUD operations and other database interactions for the user-related entities.
 * </p>
 *
 * <p>
 * The repositories in this package extend Spring Data JPA repositories, leveraging Spring Data's powerful features for data access.
 * </p>
 *
 *
 * Example usage:
 *
 * <pre>
 * {@code
 * @Autowired
 * private UserRepository userRepository;
 *
 * public void someMethod() {
 *     User user = userRepository.findById(1L).orElse(null);
 *     // perform operations with the user
 * }
 * }
 * </pre>
 *
 *
 * @see org.springframework.data.jpa.repository.JpaRepository
 */
package com.digitalsanctuary.spring.user.persistence.repository;
