package com.medscan.delivery;

/**
 * Machine à états d'une commande de livraison pharmaceutique (MOD-09).
 * 
 * Transitions valides :
 * CREATED ➔ ASSIGNED ➔ PICKED_UP ➔ IN_TRANSIT ➔ DELIVERED (via validation OTP)
 * Tout état intermédiaire peut passer à FAILED ou CANCELLED.
 */
public enum DeliveryStatus {
    /** Colis scellé préparé en officine, mission créée en attente de prise en charge */
    CREATED,

    /** Mission attribuée à un livreur assermenté */
    ASSIGNED,

    /** Le livreur a scanné et retiré le colis scellé en officine */
    PICKED_UP,

    /** Le livreur est en route vers l'adresse du patient destinataire */
    IN_TRANSIT,

    /** Colis remis en main propre au destinataire après validation réussie de l'OTP 6 chiffres */
    DELIVERED,

    /** Échec de la livraison (destinataire absent, trop d'échecs OTP, incident) */
    FAILED,

    /** Commande de livraison annulée */
    CANCELLED
}
