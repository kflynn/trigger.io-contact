//
//  contact_Util.m
//  ForgeTemplate
//
//  Created by James Brady on 05/10/2012.
//  Copyright (c) 2012 Trigger Corp. All rights reserved.
//

#import "contact_Util.h"

@implementation contact_Util

+ (NSDictionary*) dictFrom:(ABRecordRef)contact withFields:(NSArray *)fields {
    NSDictionary *data = [[NSMutableDictionary alloc] init];
    
    [data setValue:[NSString stringWithFormat:@"%d", ABRecordGetRecordID(contact)] forKey:@"id"];
    
    NSString *displayName = @"";
    if (ABRecordCopyValue(contact, kABPersonPrefixProperty) != NULL) {
        displayName = [displayName stringByAppendingFormat:@"%@ ", (__bridge_transfer NSString *)ABRecordCopyValue(contact, kABPersonPrefixProperty)];
    }
    if (ABRecordCopyValue(contact, kABPersonFirstNameProperty) != NULL) {
        displayName = [displayName stringByAppendingFormat:@"%@ ", (__bridge_transfer NSString *)ABRecordCopyValue(contact, kABPersonFirstNameProperty)];
    }
    if (ABRecordCopyValue(contact, kABPersonMiddleNameProperty) != NULL) {
        displayName = [displayName stringByAppendingFormat:@"%@ ", (__bridge_transfer NSString *)ABRecordCopyValue(contact, kABPersonMiddleNameProperty)];
    }
    if (ABRecordCopyValue(contact, kABPersonLastNameProperty) != NULL) {
        displayName = [displayName stringByAppendingFormat:@"%@ ", (__bridge_transfer NSString *)ABRecordCopyValue(contact, kABPersonLastNameProperty)];
    }
    if (ABRecordCopyValue(contact, kABPersonSuffixProperty) != NULL) {
        displayName = [displayName stringByAppendingFormat:@"%@ ", (__bridge_transfer NSString *)ABRecordCopyValue(contact, kABPersonSuffixProperty)];
    }
    displayName = [displayName stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceCharacterSet]];
	
    [data setValue:displayName forKey:@"displayName"];
	
	if ([fields containsObject:@"photos"]) {
		if (ABPersonHasImageData(contact)) {
			UIImage *image = [[UIImage alloc] initWithData:(__bridge_transfer NSData *)ABPersonCopyImageDataWithFormat(contact, kABPersonImageFormatThumbnail)];
			NSData *imageData = UIImageJPEGRepresentation(image, 0.8 );
			
			NSString *base64Data = [imageData base64EncodingWithLineLength:0];
			
			[data setValue:[[NSArray alloc] initWithObjects:[[NSDictionary alloc] initWithObjectsAndKeys:
															 [NSString stringWithFormat:@"data:image/jpg;base64,%@", base64Data],
															 @"value",
															 [[NSNumber alloc] initWithBool:NO],
															 @"pref",
															 nil], nil] forKey:@"photos"];
		}
	}
    
	if ([fields containsObject:@"nickname"]) {
		[data setValue:(__bridge_transfer NSString *)ABRecordCopyValue(contact, kABPersonNicknameProperty) forKey:@"nickname"];
	}
	
	if ([fields containsObject:@"note"]) {
		[data setValue:(__bridge_transfer NSString *)ABRecordCopyValue(contact, kABPersonNoteProperty) forKey:@"note"];
	}
    
	if ([fields containsObject:@"birthday"]) {
		// TODO: Return as date
		[data setValue:[(__bridge_transfer NSDate *)ABRecordCopyValue(contact, kABPersonBirthdayProperty) description] forKey:@"birthday"];
	}
    
	if ([fields containsObject:@"name"]) {
		[data setValue:[[NSDictionary alloc] initWithObjectsAndKeys:
						(__bridge_transfer NSString *)ABRecordCopyValue(contact, kABPersonLastNameProperty), @"familyName",
						[data objectForKey:@"displayName"], @"formatted",
						(__bridge_transfer NSString *)ABRecordCopyValue(contact, kABPersonFirstNameProperty), @"givenName",
						(__bridge_transfer NSString *)ABRecordCopyValue(contact, kABPersonPrefixProperty), @"honorificPrefix",
						(__bridge_transfer NSString *)ABRecordCopyValue(contact, kABPersonSuffixProperty), @"honorificSuffix",
						(__bridge_transfer NSString *)ABRecordCopyValue(contact, kABPersonMiddleNameProperty), @"middleName", nil] forKey:@"name"];
	}
	
	if ([fields containsObject:@"urls"]) {
		ABMultiValueRef urlMultiValue = ABRecordCopyValue(contact, kABPersonURLProperty);
		
		NSMutableArray *urls = [[NSMutableArray alloc] initWithCapacity:ABMultiValueGetCount(urlMultiValue)];
		
		for (int x = 0; x < ABMultiValueGetCount(urlMultiValue); x++) {
			CFStringRef labelRef = ABMultiValueCopyLabelAtIndex(urlMultiValue, x);
			NSString *label = (__bridge_transfer NSString *)labelRef;
			
			
			// Specified label constants
			// https://www.pivotaltracker.com/story/show/33995229
			// http://www.iphonedevsdk.com/forum/iphone-sdk-development/97478-abpeoplepickernavigationcontroller-crash.html
			// Exchange can return NULL for labelRef
			if (labelRef != NULL) {
				if (CFStringCompare(labelRef, kABPersonHomePageLabel, 0) == kCFCompareEqualTo) {
					label = @"homepage";
				} else if (CFStringCompare(labelRef, kABWorkLabel, 0) == kCFCompareEqualTo) {
					label = @"work";
				} else if (CFStringCompare(labelRef, kABHomeLabel, 0) == kCFCompareEqualTo) {
					label = @"home";
				} else if (CFStringCompare(labelRef, kABOtherLabel, 0) == kCFCompareEqualTo) {
					label = @"other";
				}
			} else {
				label = @"other";
			}
			
			[urls addObject:[[NSDictionary alloc] initWithObjectsAndKeys:[[NSNumber alloc] initWithBool:NO], @"pref", (__bridge_transfer NSString *)ABMultiValueCopyValueAtIndex(urlMultiValue, x), @"value", label, @"type", nil]];
		}
		[data setValue:urls forKey:@"urls"];
		CFRelease(urlMultiValue);
	}
    
    if ([fields containsObject:@"phoneNumbers"]) {
		ABMultiValueRef phoneMultiValue = ABRecordCopyValue(contact, kABPersonPhoneProperty);
		
		NSMutableArray *phones = [[NSMutableArray alloc] initWithCapacity:ABMultiValueGetCount(phoneMultiValue)];
		
		for (int x = 0; x < ABMultiValueGetCount(phoneMultiValue); x++) {
			CFStringRef labelRef = ABMultiValueCopyLabelAtIndex(phoneMultiValue, x);
			NSString *label = (__bridge_transfer NSString *)labelRef;
			
			// Specified label constants
			if (labelRef != NULL) {
				if (CFStringCompare(labelRef, kABPersonPhoneMobileLabel, 0) == kCFCompareEqualTo) {
					label = @"mobile";
				} else if (CFStringCompare(labelRef, kABPersonPhoneIPhoneLabel, 0) == kCFCompareEqualTo) {
					label = @"iPhone";
				} else if (CFStringCompare(labelRef, kABPersonPhoneMainLabel, 0) == kCFCompareEqualTo) {
					label = @"main";
				} else if (CFStringCompare(labelRef, kABPersonPhoneHomeFAXLabel, 0) == kCFCompareEqualTo) {
					label = @"home_fax";
				} else if (CFStringCompare(labelRef, kABPersonPhoneWorkFAXLabel, 0) == kCFCompareEqualTo) {
					label = @"work_fax";
				} else if (CFStringCompare(labelRef, kABPersonPhonePagerLabel, 0) == kCFCompareEqualTo) {
					label = @"pager";
				} else if (CFStringCompare(labelRef, kABWorkLabel, 0) == kCFCompareEqualTo) {
					label = @"work";
				} else if (CFStringCompare(labelRef, kABHomeLabel, 0) == kCFCompareEqualTo) {
					label = @"home";
				} else if (CFStringCompare(labelRef, kABOtherLabel, 0) == kCFCompareEqualTo) {
					label = @"other";
				}
			} else {
				label = @"other";
			}
			
			[phones addObject:[[NSDictionary alloc] initWithObjectsAndKeys:[[NSNumber alloc] initWithBool:NO], @"pref", (__bridge_transfer NSString *)ABMultiValueCopyValueAtIndex(phoneMultiValue, x), @"value", label, @"type", nil]];
		}
		[data setValue:phones forKey:@"phoneNumbers"];
		CFRelease(phoneMultiValue);
	}
    
    if ([fields containsObject:@"emails"]) {
		ABMultiValueRef emailMultiValue = ABRecordCopyValue(contact, kABPersonEmailProperty);
		
		NSMutableArray *emails = [[NSMutableArray alloc] initWithCapacity:ABMultiValueGetCount(emailMultiValue)];
		
		for (int x = 0; x < ABMultiValueGetCount(emailMultiValue); x++) {
			CFStringRef labelRef = ABMultiValueCopyLabelAtIndex(emailMultiValue, x);
			NSString *label = (__bridge_transfer NSString *)labelRef;
			
			if (labelRef != NULL) {
				if (CFStringCompare(labelRef, kABWorkLabel, 0) == kCFCompareEqualTo) {
					label = @"work";
				} else if (CFStringCompare(labelRef, kABHomeLabel, 0) == kCFCompareEqualTo) {
					label = @"home";
				} else if (CFStringCompare(labelRef, kABOtherLabel, 0) == kCFCompareEqualTo) {
					label = @"other";
				}
			} else {
				label = @"other";
			}
			
			[emails addObject:[[NSDictionary alloc] initWithObjectsAndKeys:[[NSNumber alloc] initWithBool:NO], @"pref", (__bridge_transfer NSString *)ABMultiValueCopyValueAtIndex(emailMultiValue, x), @"value", label, @"type", nil]];
		}
		[data setValue:emails forKey:@"emails"];
		CFRelease(emailMultiValue);
	}
    
    if ([fields containsObject:@"ims"]) {
		ABMultiValueRef imMultiValue = ABRecordCopyValue(contact, kABPersonInstantMessageProperty);
		
		NSMutableArray *ims = [[NSMutableArray alloc] initWithCapacity:ABMultiValueGetCount(imMultiValue)];
		
		for (int x = 0; x < ABMultiValueGetCount(imMultiValue); x++) {
			NSDictionary *dict = (__bridge_transfer NSDictionary *)ABMultiValueCopyValueAtIndex(imMultiValue, x);
			
			[ims addObject:[[NSDictionary alloc] initWithObjectsAndKeys:[[NSNumber alloc] initWithBool:NO], @"pref", [dict objectForKey:(__bridge_transfer NSString *)kABPersonInstantMessageUsernameKey], @"value", [dict objectForKey:(__bridge_transfer NSString *)kABPersonInstantMessageServiceKey], @"type", nil]];
		}
		[data setValue:ims forKey:@"ims"];
		CFRelease(imMultiValue);
	}
    
    if ([fields containsObject:@"organizations"]) {
		if ((__bridge_transfer NSString *)ABRecordCopyValue(contact, kABPersonOrganizationProperty) != nil || (__bridge_transfer NSString *)ABRecordCopyValue(contact, kABPersonDepartmentProperty) != nil || (__bridge_transfer NSString *)ABRecordCopyValue(contact, kABPersonJobTitleProperty) != nil) {
			[data setValue:[[NSArray alloc] initWithObjects:[[NSDictionary alloc] initWithObjectsAndKeys:
															 (__bridge_transfer NSString *)ABRecordCopyValue(contact, kABPersonOrganizationProperty),
															 @"name",
															 (__bridge_transfer NSString *)ABRecordCopyValue(contact, kABPersonDepartmentProperty),
															 @"department",
															 (__bridge_transfer NSString *)ABRecordCopyValue(contact, kABPersonJobTitleProperty),
															 @"title",
															 nil], nil] forKey:@"organizations"];
		} else {
			[data setValue:[[NSArray alloc] init] forKey:@"organizations"];
		}
	}
    
    if ([fields containsObject:@"addresses"]) {
		ABMultiValueRef addressMultiValue = ABRecordCopyValue(contact, kABPersonAddressProperty);
		
		NSMutableArray *addresses = [[NSMutableArray alloc] initWithCapacity:ABMultiValueGetCount(addressMultiValue)];
		
		for (int x = 0; x < ABMultiValueGetCount(addressMultiValue); x++) {
			CFStringRef labelRef = ABMultiValueCopyLabelAtIndex(addressMultiValue, x);
			NSString *label = (__bridge_transfer NSString *)labelRef;
			
			if (labelRef != NULL) {
				if (CFStringCompare(labelRef, kABWorkLabel, 0) == kCFCompareEqualTo) {
					label = @"work";
				} else if (CFStringCompare(labelRef, kABHomeLabel, 0) == kCFCompareEqualTo) {
					label = @"home";
				} else if (CFStringCompare(labelRef, kABOtherLabel, 0) == kCFCompareEqualTo) {
					label = @"other";
				}
			} else {
				label = @"other";
			}
			
			NSDictionary *dict = (__bridge_transfer NSDictionary *)ABMultiValueCopyValueAtIndex(addressMultiValue, x);
			
			NSMutableDictionary *address = [[NSMutableDictionary alloc] initWithObjectsAndKeys:
											[[NSNumber alloc] initWithBool:NO], @"pref",
											[dict objectForKey:(__bridge_transfer NSString *)kABPersonAddressStreetKey], @"streetAddress",
											[dict objectForKey:(__bridge_transfer NSString *)kABPersonAddressCityKey], @"locality",
											[dict objectForKey:(__bridge_transfer NSString *)kABPersonAddressStateKey], @"region",
											[dict objectForKey:(__bridge_transfer NSString *)kABPersonAddressZIPKey], @"postalCode",
											[dict objectForKey:(__bridge_transfer NSString *)kABPersonAddressCountryKey], @"country",
											label, @"type",
											nil];
			
			NSString *formatted = @"";
			if ([address objectForKey:@"streetAddress"]) {
				formatted = [formatted stringByAppendingFormat:@"%@\n", [address objectForKey:@"streetAddress"]];
			}
			if ([address objectForKey:@"locality"]) {
				formatted = [formatted stringByAppendingFormat:@"%@\n", [address objectForKey:@"locality"]];
			}
			if ([address objectForKey:@"region"]) {
				formatted = [formatted stringByAppendingFormat:@"%@\n", [address objectForKey:@"region"]];
			}
			if ([address objectForKey:@"postalCode"]) {
				formatted = [formatted stringByAppendingFormat:@"%@\n", [address objectForKey:@"postalCode"]];
			}
			if ([address objectForKey:@"country"]) {
				formatted = [formatted stringByAppendingFormat:@"%@\n", [address objectForKey:@"country"]];
			}
			formatted = [formatted stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
			
			[address setValue:formatted forKey:@"formatted"];
			
			[addresses addObject:address];
		}
		[data setValue:addresses forKey:@"addresses"];
		CFRelease(addressMultiValue);
	}
	
    return data;
}

+ (NSError *)contactError:(NSString *)description {
    
    NSDictionary *errorDetail = 
        @{ NSLocalizedDescriptionKey: description };
    
    NSError *thisError =
        [NSError errorWithDomain:@"iOSContactDomain"
                            code:1
                        userInfo:errorDetail];

    return thisError;
}

+ (bool)mapElements:(ABRecordRef)newPerson
           workDict:(NSDictionary *)workDict
           elements:(NSArray *)elements
          error_out:(CFErrorRef *)error_out {
    bool result = YES;
    // Cycle over all of our elements, looking for hits within the workDict.
    
    for (NSArray *element in elements) {
        NSString *key = element[0];
        NSNumber *propNum = element[1];
        ABPropertyID prop = [propNum intValue];
        
        id value = workDict[key];
        
        // If we got a value...
        
        if (value) {
            // ...go ahead and stuff it into newPerson.
            result = ABRecordSetValue(newPerson,
                                      prop, (__bridge CFTypeRef)value,
                                      error_out);
            
            if (!result) {
                // Something went wrong; get outta here.
                break;
            }
        }
    }
    
    return result;
}

+ (bool)mapUnivalues:(ABRecordRef)newPerson
         contactDict:(NSDictionary *)contactDict
        propertyMaps:(NSArray *)propertyMaps
           error_out:(CFErrorRef *)error_out {
    bool result = YES;
    
    for (NSArray *map in propertyMaps) {
        NSString *workKey = map[0];
        NSArray *elements = map[1];
        
        // Assume that they want to look at the top-level dictionary...
        NSDictionary *workDict = contactDict;
        
        // ...then check to see if they gave a key.
        if ((id) workKey != [NSNull null]) {
            // Yup.  Shift workDict down.
            workDict = contactDict[workKey];
        }
        
        if (!workDict || ([workDict count] <= 0)) {
            // The dict they want to look at doesn't exist or is empty.
            // Skip it.
            continue;
        }
        
        result = [self mapElements:newPerson
                          workDict:workDict
                          elements:elements
                         error_out:error_out];
    }

    return result;
}

+ (bool)mapMultiValues:(ABRecordRef)newPerson
           contactDict:(NSDictionary *)contactDict
     multiPropertyMaps:(NSArray *)multiPropertyMaps
             error_out:(CFErrorRef *)error_out {
    bool result = YES;
    
    for (NSArray *multiMap in multiPropertyMaps) {
        NSString *workKey = multiMap[0];
        NSNumber *propNum = multiMap[1];
        NSDictionary *typeMap = multiMap[2];
        NSDictionary *keyMap = multiMap[3];
        
        // Here, we MUST have a workKey...
        NSAssert((id) workKey != [NSNull null],
                 @"workKey cannot be null");
        
        // ...and it's the name of an array, not of another dictionary.
        NSArray *workArray = contactDict[workKey];
        
        if (!workArray || ([workArray count] <= 0)) {
            // The array they want to look at doesn't exist or is empty.
            // Skip it.
            continue;
        }
        
        // OK.  Create a new multivalue.  If we have a keyMap, this should
        // be a MultiDictionary, otherwise a MultiString.

        ABPropertyType mvType = 
            (((id)keyMap == [NSNull null]) ? kABMultiStringPropertyType
                                           : kABMultiDictionaryPropertyType);

        ABMutableMultiValueRef multiValue =
            ABMultiValueCreateMutable(mvType);
        
        // Cycle over the workArray grabbing elements.

        for (NSDictionary *workElement in workArray) {
            NSString *type = workElement[@"type"];
            // ignore "pref" -- we don't support it.
            
            NSString *label = typeMap[type];
            
            if (!label || ([label length] <= 0)) {
                // Not in the map; just use the type itself.
                label = type;
            }
             
            // OK.  Are we a MultiString?

            if (mvType == kABMultiStringPropertyType) {
                // Yup.  Grab the value and insert.
                id value = workElement[@"value"];

                if (value) {
                    result =
                        ABMultiValueAddValueAndLabel(multiValue,
                                                     (__bridge CFTypeRef)value,
                                                     (__bridge CFStringRef)label,
                                                     NULL);
                }
            }
            else {
                // MultiDict.  Create a new dictionary...

                NSMutableDictionary *valDict =
                    [NSMutableDictionary dictionaryWithCapacity:4];

                for (NSArray *keyMapEl in keyMap) {
                    NSString *jsonKey = keyMapEl[0];
                    NSString *dictKey = keyMapEl[1];

                    id value = workElement[jsonKey];

                    if (value) {
                        valDict[dictKey] = value;
                    }
                }
                
                // Finally, add to our multivalue.
                result =
                    ABMultiValueAddValueAndLabel(multiValue,
                                                 (__bridge CFTypeRef)valDict,
                                                 (__bridge CFStringRef)label,
                                                 NULL);
            }
                
            if (!result) {
                // That ain't good.
                NSString *errStr =
                    [NSString stringWithFormat:@"couldn't add %@ %@ to contact",
                        workKey, label];
                    
                NSError *thisError = [self contactError:errStr];
                *error_out = (__bridge CFErrorRef)thisError;
                break;
            }
        }
        
        if (result) {
            // Add it to the contact.
            result = ABRecordSetValue(newPerson, [propNum intValue],
                                      multiValue, error_out);
        }
        
        if (!result) {
            // Aarrrrgh.
            break;
        }
    }

    return result;
}

+ (ABRecordRef)contactCreateFrom:(NSDictionary *)dict
                       error_out:(CFErrorRef *)error_out {
    // Create a new person record.
    ABRecordRef newPerson = ABPersonCreate();
    bool result = YES;
    
    NSArray *propertyMaps =
        @[
           @[ [NSNull null],
              @[ @[ @"note", @(kABPersonNoteProperty) ],
                 @[ @"nickname", @(kABPersonNicknameProperty) ],
              ],
           ],
           @[ @"name",
              @[ @[ @"givenName", @(kABPersonFirstNameProperty) ],
                 @[ @"familyName", @(kABPersonLastNameProperty) ],
                 @[ @"honorificPrefix", @(kABPersonPrefixProperty) ],
                 @[ @"honorificSuffix", @(kABPersonSuffixProperty) ],
                 @[ @"middleName", @(kABPersonMiddleNameProperty) ],
              ],
           ],
        ];
    
    // multiStringMaps describes how to map JSON elements like emails
    // to ABPerson elements.  The JSON object for emails is an array:
    // 
    // emails: [ { type: "home", pref: false, value: "jane@jane.com" },
    //           { type: "work", pref: false, value: "jane@cald.com" } ]
    //
    // Each element in the array is a dict with type, pref, and value.
    //
    // Each element in multiStringMaps is an NSArray:
    //
    // @[ object-key, @(kABPerson-property), typeMap, keyMap ]
    //
    // object-key is e.g. "emails"
    // kABPerson-property for emails would be kABPersonEmailProperty
    // 
    // typeMap is an NSDict that maps type values ("home", "work") to
    // kABPerson labels, e.g.:
    // 
    // @{ @"work": (__bridge NSString *) kABWorkLabel,
    //    @"home": (__bridge NSString *) kABHomeLabel,
    // }
    //
    // etc.
    //
    // keyMap is an optional _ARRAY_ that lets us know that the value is 
    // split across many keys, not just value, and defines the mapping
    // between JSON keys and kABPerson keys.  It may be an NSNull if not
    // needed.
    // 
    // We use keyMap for things like addresses, which looks like this in
    // JSON:
    // 
    // addresses: [ { country: "United States",
    //                formatted: display-address-with-newlines,
    //                locality: "Cupertino", 
    //                postalCode: "95014",
    //                pref: false,
    //                region: "CA",
    //                streetAddress: "123 Main Street",
    //                type: "home" }, 
    //               ... ]
    // 
    // type and pref are still present, and type needs to be handled just
    // as above; however, there is no value, and all the other keys need
    // to be managed.  So we use a keyMap, part of which (leaving out the
    // __bridge'ing we have to do) looks like:
    //
    // @[ @[ @"streetAddress", kABPersonAddressStreetKey ],
    //    @[ @"locality", kABPersonAddressCityKey ],
    //    ... ]

    NSArray *multiPropertyMaps =
        @[
           @[ @"phoneNumbers", @(kABPersonPhoneProperty),
              @{ @"mobile": (__bridge NSString *) kABPersonPhoneMobileLabel,
                 @"iPhone": (__bridge NSString *) kABPersonPhoneIPhoneLabel,
                 @"main": (__bridge NSString *) kABPersonPhoneMainLabel,
                 @"home_fax": (__bridge NSString *) kABPersonPhoneHomeFAXLabel,
                 @"work_fax": (__bridge NSString *) kABPersonPhoneWorkFAXLabel,
                 @"pager": (__bridge NSString *) kABPersonPhonePagerLabel,
                 @"work": (__bridge NSString *) kABWorkLabel,
                 @"home": (__bridge NSString *) kABHomeLabel,
                 @"other": (__bridge NSString *) kABOtherLabel,
              },
              [NSNull null]
           ],
           @[ @"emails", @(kABPersonEmailProperty),
              @{ @"work": (__bridge NSString *) kABWorkLabel,
                 @"home": (__bridge NSString *) kABHomeLabel,
                 @"other": (__bridge NSString *) kABOtherLabel,
              },
              [NSNull null],
           ],
           @[ @"urls", @(kABPersonURLProperty),
              @{ @"homepage": (__bridge NSString *) kABPersonHomePageLabel,
                 @"work": (__bridge NSString *) kABWorkLabel,
                 @"home": (__bridge NSString *) kABHomeLabel,
                 @"other": (__bridge NSString *) kABOtherLabel,
              },
              [NSNull null],
           ],
           @[ @"ims", @(kABPersonInstantMessageProperty),
              @{ @"work": (__bridge NSString *) kABWorkLabel,
                 @"home": (__bridge NSString *) kABHomeLabel,
                 @"other": (__bridge NSString *) kABOtherLabel,
              },
              @[ @[ @"value",
                    (__bridge NSString *) kABPersonInstantMessageUsernameKey ],
                 @[ @"type",
                    (__bridge NSString *) kABPersonInstantMessageServiceKey ],
              ],
           ],
           @[ @"addresses", @(kABPersonAddressProperty),
              @{ @"work": (__bridge NSString *) kABWorkLabel,
                 @"home": (__bridge NSString *) kABHomeLabel,
                 @"other": (__bridge NSString *) kABOtherLabel,
              },
              @[ @[ @"streetAddress",
                     (__bridge NSString *) kABPersonAddressStreetKey ],
                 @[ @"locality",
                     (__bridge NSString *) kABPersonAddressCityKey ],
                 @[ @"region",
                     (__bridge NSString *) kABPersonAddressStateKey ],
                 @[ @"postalCode",
                     (__bridge NSString *) kABPersonAddressZIPKey ],
                 @[ @"country",
                     (__bridge NSString *) kABPersonAddressCountryKey ],
              ],
           ],
        ];

    // Organizations, sadly, are just weird: the iOS address book only
    // supports one of them, and it uses top-level properties to do it, but
    // JSON can store multiple values in an array where each member of the
    // array looks like a univalue.

    NSArray *orgMap =
        @[ @[ @"name", @(kABPersonOrganizationProperty) ],
           @[ @"department", @(kABPersonDepartmentProperty) ],
           @[ @"title", @(kABPersonJobTitleProperty) ] ];

    // OK.  Map the univalues...
    result = [self mapUnivalues:newPerson
                    contactDict:dict
                   propertyMaps:propertyMaps
                      error_out:error_out ];

    if (result) {
        // ...then the multivalues...
        result = [self mapMultiValues:newPerson
                          contactDict:dict
                    multiPropertyMaps:multiPropertyMaps
                            error_out:error_out];
    }
    
    // ...and then pick up the weird special stuff.

    if (result) {
        // Organizations are weird, as noted above.
        
        NSArray *orgs = dict[@"organizations"];
        
        if (orgs && ([orgs count] > 0)) {
            // Ignore all but the first org.
            result = [self mapElements:newPerson
                              workDict:orgs[0]
                              elements:orgMap
                             error_out:error_out];
        }
    }
    
    if (result) {
        // Birthday is special, since it has to be a date, not a string.
        // (Man, handling dates sucks.)
        
        NSString *bDayString = dict[@"birthday"];
        
        if (bDayString && ([bDayString length] > 0)) {
            NSDateFormatter *format = [[NSDateFormatter alloc] init];
            [format setDateFormat:@"yyyy-MM-dd HH:mm:ss Z"];

            NSDate *birthday = [format dateFromString:bDayString];
            
            if (birthday) {
                result = ABRecordSetValue(newPerson,
                                          kABPersonBirthdayProperty,
                                          (__bridge CFTypeRef)birthday,
                                          error_out);
            }
            else {
                NSLog(@"couldn't convert %@ to NSDate", bDayString);
            }
        }
    }
    
    if (result) {
        // Photos are special, too.  We need to take the first one and
        // turn it into an image.  Thankfully, NSURL understands the
        // data: URI...
        
        NSArray *photos = dict[@"photos"];
        
        if (photos && ([photos count] > 0))  {
            NSString *photoString = photos[0][@"value"];

            if (photoString && ([photoString length] > 0)) {
                NSURL *photoURL = [NSURL URLWithString:photoString];
                NSData *photoData = [NSData dataWithContentsOfURL:photoURL];

                if (photoData && ([photoData length] > 0)) {
                    // Finally!
                    result = ABPersonSetImageData(newPerson,
                                                  (__bridge CFDataRef)photoData,
                                                  error_out);
                }
            }
        }
    }
    
cleanup:
    if (!result) {
        // Something went wrong.  Release our person...
        CFRelease(newPerson);
        
        // ...and return nil.
        newPerson = nil;
    }
    
    return newPerson;
}

@end
