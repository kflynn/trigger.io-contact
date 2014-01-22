//
//  contact_API.m
//  Forge
//
//  Created by Connor Dunn on 14/03/2012.
//  Copyright (c) 2012 Trigger Corp. All rights reserved.
//

#import <AddressBookUI/AddressBookUI.h>

#import "contact_API.h"
#import "contact_Delegate.h"
#import "contact_Util.h"

@implementation contact_API

+ (void)select:(ForgeTask*)task {
	ABPeoplePickerNavigationController *picker = [[ABPeoplePickerNavigationController alloc] init];
	
	contact_Delegate *delegate = [[contact_Delegate alloc] initWithTask:task];
	picker.peoplePickerDelegate = delegate;
	
	[[[ForgeApp sharedApp] viewController] presentModalViewController:picker animated:YES];
}

+ (void)selectById:(ForgeTask *)task id:(NSString *) contactId {
    ABAddressBookRef addressBook = ABAddressBookCreate();
    
    if (![self addressBookAccessGranted:addressBook]) {
        [task error:@"User didn't grant access to address book" type:@"EXPECTED_FAILURE" subtype:nil];
        return;
    } else {
        ABRecordRef person = ABAddressBookGetPersonWithRecordID(addressBook, (ABRecordID)[contactId intValue]);
        [task success:[contact_Util dictFrom:person withFields:@[@"name", @"nickname", @"phoneNumbers", @"emails", @"addresses", @"ims", @"organizations", @"birthday", @"note", @"photos", @"categories", @"urls"]]];
        return;
    }
}

+ (void)selectAll:(ForgeTask*)task fields:(NSArray*) fields {
    ABAddressBookRef addressBook = ABAddressBookCreate();
    
    if (![self addressBookAccessGranted:addressBook]) {
        [task error:@"User didn't grant access to address book" type:@"EXPECTED_FAILURE" subtype:nil];
        return;
    }
    else {
        NSArray *thePeople = (NSArray *)CFBridgingRelease(ABAddressBookCopyArrayOfAllPeople(addressBook));
        NSMutableArray *serialisedPeople = [NSMutableArray arrayWithCapacity:[thePeople count]];
        for (int i=0; i < [thePeople count]; i++) {
            ABRecordRef person = CFBridgingRetain([thePeople objectAtIndex:i]);
            [serialisedPeople addObject:[contact_Util dictFrom:person withFields:fields]];
        }
        [task success:serialisedPeople];
        return;
    }
}

+ (void)selectAll:(ForgeTask *)task {
    [contact_API selectAll:task
                       fields:@[ @"phoneNumbers", @"emailAddresses" ]];
}

// add:contact: add a single contact to the user's address book.
//
// task: a Forge task
// contactDict: a single contact, in the form of an NSDictionary that
// basically conforms to the W3C Contact specification (just in an 
// NSDictionary, rather than a JSON string).
//
// On success, calls [task success] and passes the ID of the newly-created
// contact.  On failure, calls [task error] with a message that is, we hope,
// at least mostly helpful.
//
// Note also that the translation from the dictionary to an iOS ABPerson 
// is deliberately paranoid: it's OK if things are missing, or if the
// contactDict contains things we don't care about, but it is NOT OK if
// we see an error copying a data element into our new ABPerson.

+ (void)add:(ForgeTask*)task contact:(NSDictionary *)contactDict {
    NSLog(@"Called add: %@", contactDict);
    
    ABAddressBookRef addressBook = ABAddressBookCreate();
    
    if (![self addressBookAccessGranted:addressBook]) {
        [task error:@"User didn't grant access to address book" type:@"EXPECTED_FAILURE" subtype:nil];
        return;
    }
    else {
        CFErrorRef error;

        // Let contact_Util handle the heavy lifting for us.
        ABRecordRef newPerson = [contact_Util contactCreateFrom:contactDict
                                                         error_out:&error];
        
        if (!newPerson) {
            NSLog(@"error! %@", error);
            [task error:@"couldn't create new record" type:@"UNEXPECTED_FAILURE" subtype:nil];
        }
        else if (!ABAddressBookAddRecord(addressBook, newPerson, &error)) {
            NSLog(@"error! %@", error);
            [task error:@"couldn't add new record" type:@"UNEXPECTED_FAILURE" subtype:nil];
        }
        else if (!ABAddressBookSave(addressBook, &error)) {
            NSLog(@"error! %@", error);
            [task error:@"couldn't save address book" type:@"UNEXPECTED_FAILURE" subtype:nil];
        }
        else {
            // FINALLY.
            NSString *idStr =
                [NSString stringWithFormat:@"%d", ABRecordGetRecordID(newPerson)];

            [task success:idStr];
        }
    }
}

+ (BOOL)addressBookAccessGranted:(ABAddressBookRef)addressBook {
    __block BOOL accessGranted = NO;
    
    if (ABAddressBookRequestAccessWithCompletion != NULL) { // we're on iOS 6
        dispatch_semaphore_t sema = dispatch_semaphore_create(0);
        
        ABAddressBookRequestAccessWithCompletion(addressBook, ^(bool granted, CFErrorRef error) {
            accessGranted = granted;
            dispatch_semaphore_signal(sema);
        });
        
        dispatch_semaphore_wait(sema, DISPATCH_TIME_FOREVER);
        dispatch_release(sema);
    }
    else { // we're on iOS 5 or older
        accessGranted = YES;
    }
    return accessGranted;
}

@end
