package com.example.solimus.exceptions;

import lombok.Getter;

// Exception levée quand on essaie de créer un copropriétaire avec un email ou téléphone déjà utilisé, porte l'ID du copropriétaire existant pour que le frontend puisse proposer le lien directement
@Getter
public class CoOwnerAlreadyExistsException extends RuntimeException {

  private final Long coOwnerId; // ID du copropriétaire existant — retourné au frontend

  public CoOwnerAlreadyExistsException(String message, Long coOwnerId) {
    super(message); // on passe le message à RuntimeException
    this.coOwnerId = coOwnerId; // on stocke l'ID
  }


}